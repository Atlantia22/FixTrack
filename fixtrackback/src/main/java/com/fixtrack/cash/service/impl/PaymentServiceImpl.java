package com.fixtrack.cash.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.cash.dto.PaidAmount;
import com.fixtrack.cash.dto.PaymentRequest;
import com.fixtrack.cash.dto.PaymentSummaryResponse;
import com.fixtrack.cash.entity.Payment;
import com.fixtrack.cash.entity.PaymentType;
import com.fixtrack.cash.mapper.PaymentMapper;
import com.fixtrack.cash.repository.PaymentRepository;
import com.fixtrack.cash.service.PaymentService;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.Money;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.events.InterventionEventPublisher;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.mapper.InterventionCode;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.person.service.PersonService;

import lombok.RequiredArgsConstructor;

// Abonos, cobro final y devoluciones de una orden; el saldo se calcula contra el total de la orden
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

	private final PaymentRepository paymentRepository;
	private final InterventionPricingService pricingService;
	private final PersonService personService;
	private final PaymentMapper paymentMapper;
	private final InterventionEventPublisher interventionEvents;

	// Abono mientras la orden siga abierta, cobro final solo lista para entregar y devolución hasta el saldo a favor
	@Override
	@Transactional
	public PaymentSummaryResponse register(Long interventionId, PaymentRequest request, AuthenticatedUser actor) {
		Intervention intervention = pricingService.getEntity(interventionId);
		String code = InterventionCode.of(intervention);
		BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
		BigDecimal due = pricingService.amountDue(intervention);
		BigDecimal paid = netPaid(interventionId);

		if (intervention.getWarrantyOf() != null && request.type() != PaymentType.REFUND) {
			throw new ConflictException("La orden " + code + " es una garantía: no tiene cobro");
		}

		switch (request.type()) {
			case DEPOSIT -> {
				if (intervention.getStatus().isClosed()) {
					throw new ConflictException("La orden " + code + " está cerrada; no recibe abonos");
				}
			}
			case FINAL -> {
				if (intervention.getStatus() != InterventionStatus.READY) {
					throw new ConflictException("El cobro final se registra cuando la orden " + code
							+ " está lista para entregar");
				}
				BigDecimal balance = due.subtract(paid);
				if (amount.compareTo(balance) > 0) {
					throw new ConflictException("El cobro de " + Money.format(amount) + " supera el saldo pendiente de "
							+ Money.format(balance.max(BigDecimal.ZERO)));
				}
			}
			case REFUND -> {
				BigDecimal refundable = paid.subtract(due);
				if (amount.compareTo(refundable) > 0) {
					throw new ConflictException(refundable.signum() > 0
							? "La devolución de " + Money.format(amount) + " supera el saldo a favor del cliente de "
									+ Money.format(refundable)
							: "El cliente no tiene saldo a favor en la orden " + code);
				}
			}
		}

		paymentRepository.save(Payment.builder()
				.intervention(intervention)
				.type(request.type())
				.method(request.method())
				.amount(amount)
				.receivedBy(personService.getEntity(actor.id()))
				.build());
		// El saldo de la orden cambió: Salida y el detalle de la orden se actualizan en las otras pantallas
		interventionEvents.updated(intervention);
		return summarize(intervention);
	}

	@Override
	@Transactional(readOnly = true)
	public PaymentSummaryResponse findByIntervention(Long interventionId) {
		return summarize(pricingService.getEntity(interventionId));
	}

	@Override
	@Transactional(readOnly = true)
	public BigDecimal netPaid(Long interventionId) {
		return paymentRepository.sumNetPaid(interventionId).setScale(2, RoundingMode.HALF_UP);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, BigDecimal> netPaidByIntervention(Collection<Long> interventionIds) {
		if (interventionIds.isEmpty()) {
			return Map.of();
		}
		return paymentRepository.sumNetPaidByIntervention(interventionIds).stream()
				.collect(Collectors.toMap(PaidAmount::interventionId,
						paid -> paid.amount().setScale(2, RoundingMode.HALF_UP)));
	}

	private PaymentSummaryResponse summarize(Intervention intervention) {
		BigDecimal total = pricingService.amountDue(intervention);
		BigDecimal paid = netPaid(intervention.getId());
		BigDecimal difference = total.subtract(paid);
		return new PaymentSummaryResponse(total, paid, difference.max(BigDecimal.ZERO).setScale(2),
				difference.negate().max(BigDecimal.ZERO).setScale(2),
				paymentRepository.findByInterventionIdOrderByCreatedAtAscIdAsc(intervention.getId()).stream()
						.map(paymentMapper::toResponse)
						.toList());
	}
}
