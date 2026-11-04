package com.fixtrack.intervention.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.NotFoundException;
import com.fixtrack.common.TooManyRequestsException;
import com.fixtrack.device.entity.DeviceModel;
import com.fixtrack.infra.security.IpAttemptLimiter;
import com.fixtrack.intervention.dto.ReceiptFile;
import com.fixtrack.intervention.dto.portal.PortalLookupRequest;
import com.fixtrack.intervention.dto.portal.PortalOrderResponse;
import com.fixtrack.intervention.dto.portal.PortalTimelineEntry;
import com.fixtrack.intervention.dto.portal.PortalWarrantyResponse;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.mapper.InterventionCode;
import com.fixtrack.intervention.repository.InterventionRepository;
import com.fixtrack.intervention.repository.InterventionStatusHistoryRepository;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.intervention.service.PortalService;
import com.fixtrack.intervention.service.ReceiptService;

import lombok.RequiredArgsConstructor;

// Portal del cliente: valida código y clave contra la orden original (también para sus garantías), cuenta los
// intentos fallidos por código y por IP, y responde solo datos para el cliente
@Service
@RequiredArgsConstructor
public class PortalServiceImpl implements PortalService {

	static final String GENERIC_FAILURE = "No encontramos una orden con ese código y clave. Verifique los datos del comprobante e intente de nuevo.";
	private static final String TOO_MANY = "Demasiados intentos desde su conexión. Espere unos minutos e intente de nuevo.";
	private static final int MAX_FAILED_ATTEMPTS = 5;
	private static final Duration LOCK_TIME = Duration.ofMinutes(15);

	private final InterventionRepository interventionRepository;
	private final InterventionStatusHistoryRepository historyRepository;
	private final InterventionPricingService pricingService;
	private final ReceiptService receiptService;
	private final PasswordEncoder passwordEncoder;
	private final IpAttemptLimiter ipAttemptLimiter;
	private final Clock clock;

	// Hash de una clave cualquiera: con códigos inexistentes también se compara, para que el tiempo de respuesta
	// no delate qué códigos existen
	private volatile String dummyHash;

	// noRollbackFor: el intento fallido y el bloqueo del código se guardan aunque la respuesta sea un error
	@Override
	@Transactional(noRollbackFor = NotFoundException.class)
	public PortalOrderResponse lookup(PortalLookupRequest request, String clientIp) {
		Access access = authenticate(request, clientIp);
		Intervention original = access.original();
		List<PortalWarrantyResponse> warranties = interventionRepository
				.findByWarrantyOfIdOrderByWarrantyNumberAsc(original.getId()).stream()
				.map(warranty -> new PortalWarrantyResponse(InterventionCode.of(warranty), warranty.getStatus(),
						warranty.getRepairOutcome(), warranty.getReceivedAt(), warranty.getEstimatedDeliveryDate(),
						warranty.getDeliveredAt(), timeline(warranty)))
				.toList();
		DeviceModel model = original.getDevice().getDeviceModel();
		LocalDate until = original.getWarrantyUntil();
		return new PortalOrderResponse(InterventionCode.of(original), model.getDeviceType(), model.getBrand().getName(),
				model.getName(), original.getStatus(), original.getRepairOutcome(), original.getReceivedAt(),
				original.getEstimatedDeliveryDate(), original.getDeliveredAt(), original.getEstimatedPrice(),
				pricingService.amountDue(original), original.getWarrantyDays(), until,
				until != null && !LocalDate.now(clock).isAfter(until), timeline(original), warranties);
	}

	@Override
	@Transactional(noRollbackFor = NotFoundException.class)
	public ReceiptFile intakeReceipt(PortalLookupRequest request, String clientIp) {
		Access access = authenticate(request, clientIp);
		return receiptService.intakeReceipt(access.requested().getId(), request.accessKey(), true);
	}

	@Override
	@Transactional(noRollbackFor = NotFoundException.class)
	public ReceiptFile deliveryReceipt(PortalLookupRequest request, String clientIp) {
		Access access = authenticate(request, clientIp);
		return receiptService.deliveryReceipt(access.requested().getId(), true);
	}

	// Código y clave contra la orden original bloqueada; con 5 fallos el código queda bloqueado 15 minutos
	private Access authenticate(PortalLookupRequest request, String clientIp) {
		if (ipAttemptLimiter.isBlocked(clientIp)) {
			throw new TooManyRequestsException(TOO_MANY);
		}

		// 1. El código existe y es de una orden original con clave (las garantías usan la de su original)
		Optional<InterventionCode.Parsed> parsed = InterventionCode.parse(request.code());
		Intervention original = parsed
				.flatMap(code -> interventionRepository.findForUpdate(code.baseId()))
				.filter(found -> found.getWarrantyOf() == null && found.getAccessKeyHash() != null)
				.orElse(null);
		if (original == null) {
			passwordEncoder.matches(request.accessKey(), dummyHash());
			throw fail(clientIp);
		}

		// 2. Un código bloqueado responde igual que una clave errada, aunque esta vez la clave sea correcta
		LocalDateTime now = LocalDateTime.now(clock);
		if (original.getLookupLockedUntil() != null && original.getLookupLockedUntil().isAfter(now)) {
			throw fail(clientIp);
		}
		if (!passwordEncoder.matches(request.accessKey(), original.getAccessKeyHash())) {
			int attempts = original.getFailedLookupAttempts() + 1;
			if (attempts >= MAX_FAILED_ATTEMPTS) {
				original.setLookupLockedUntil(now.plus(LOCK_TIME));
				attempts = 0;
			}
			original.setFailedLookupAttempts(attempts);
			throw fail(clientIp);
		}
		original.setFailedLookupAttempts(0);
		original.setLookupLockedUntil(null);

		// 3. La orden pedida: la original o una de sus garantías
		Integer warrantyNumber = parsed.get().warrantyNumber();
		if (warrantyNumber == null) {
			return new Access(original, original);
		}
		Intervention warranty = interventionRepository.findByWarrantyOfIdOrderByWarrantyNumberAsc(original.getId())
				.stream()
				.filter(candidate -> warrantyNumber.equals(candidate.getWarrantyNumber()))
				.findFirst()
				.orElseThrow(() -> new NotFoundException(GENERIC_FAILURE));
		return new Access(original, warranty);
	}

	private String dummyHash() {
		if (dummyHash == null) {
			dummyHash = passwordEncoder.encode("0000");
		}
		return dummyHash;
	}

	private NotFoundException fail(String clientIp) {
		ipAttemptLimiter.recordFailure(clientIp);
		return new NotFoundException(GENERIC_FAILURE);
	}

	// Fechas de cada cambio de estado, sin notas ni nombres del personal
	private List<PortalTimelineEntry> timeline(Intervention intervention) {
		return historyRepository.findByInterventionIdOrderByChangedAtAscIdAsc(intervention.getId()).stream()
				.map(entry -> new PortalTimelineEntry(entry.getToStatus(), entry.getChangedAt()))
				.toList();
	}

	private record Access(Intervention original, Intervention requested) {
	}
}
