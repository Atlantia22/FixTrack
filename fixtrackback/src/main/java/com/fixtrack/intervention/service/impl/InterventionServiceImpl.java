package com.fixtrack.intervention.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.SortWhitelist;
import com.fixtrack.cash.service.PaymentService;
import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.ForbiddenException;
import com.fixtrack.common.Money;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.common.PageResponse;
import com.fixtrack.device.entity.Device;
import com.fixtrack.device.service.DeviceService;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.dto.DeliveredOrderResponse;
import com.fixtrack.intervention.dto.AccessKeyResponse;
import com.fixtrack.intervention.dto.AdditionalFindingRequest;
import com.fixtrack.intervention.dto.CancelRequest;
import com.fixtrack.intervention.dto.ChargesSummary;
import com.fixtrack.intervention.dto.CreateInterventionRequest;
import com.fixtrack.intervention.dto.CreatedInterventionResponse;
import com.fixtrack.intervention.dto.DeliveryDateChangeResponse;
import com.fixtrack.intervention.dto.DeliveryRequest;
import com.fixtrack.intervention.dto.DiagnosisRequest;
import com.fixtrack.intervention.dto.FindingApprovalRequest;
import com.fixtrack.intervention.dto.InterventionDetailResponse;
import com.fixtrack.intervention.dto.InterventionFilter;
import com.fixtrack.intervention.dto.InterventionSummaryResponse;
import com.fixtrack.intervention.dto.LaborCostRequest;
import com.fixtrack.intervention.dto.OutcomeRequest;
import com.fixtrack.intervention.dto.ReadyForDeliveryResponse;
import com.fixtrack.intervention.dto.RescheduleDeliveryRequest;
import com.fixtrack.intervention.dto.StatusChangeRequest;
import com.fixtrack.intervention.dto.StatusHistoryResponse;
import com.fixtrack.intervention.dto.UnclaimedOrderResponse;
import com.fixtrack.intervention.dto.WarrantyRequest;
import com.fixtrack.intervention.events.InterventionEventPublisher;
import com.fixtrack.intervention.entity.ApprovalStatus;
import com.fixtrack.intervention.entity.DeliveryDateChange;
import com.fixtrack.intervention.entity.FindingOrigin;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionFinding;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.entity.InterventionStatusHistory;
import com.fixtrack.intervention.entity.RepairOutcome;
import com.fixtrack.intervention.event.InterventionCancelledEvent;
import com.fixtrack.intervention.mapper.InterventionCode;
import com.fixtrack.intervention.mapper.InterventionMapper;
import com.fixtrack.intervention.repository.DeliveryDateChangeRepository;
import com.fixtrack.intervention.repository.InterventionFindingRepository;
import com.fixtrack.intervention.repository.InterventionRepository;
import com.fixtrack.intervention.repository.InterventionSpecifications;
import com.fixtrack.intervention.repository.InterventionStatusHistoryRepository;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.intervention.service.InterventionService;
import com.fixtrack.intervention.service.InterventionSparePartService;
import com.fixtrack.person.dto.CustomerRequest;
import com.fixtrack.person.entity.Availability;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.service.PersonService;
import com.fixtrack.setting.entity.SettingKey;
import com.fixtrack.setting.service.SettingService;

import lombok.RequiredArgsConstructor;

// Órdenes de reparación: ingreso, técnico, entrega estimada, diagnóstico, daños adicionales, resultado, estados,
// entrega y cancelación
@Service
@RequiredArgsConstructor
public class InterventionServiceImpl implements InterventionService {

	private static final Set<Role> FRONT_DESK = EnumSet.of(Role.ADVISOR, Role.COORDINATOR, Role.ADMIN);
	private static final String NO_CONTACT_WARNING = "Conserve este comprobante, no podremos contactarlo";
	private static final SecureRandom RANDOM = new SecureRandom();
	// Puntos, espacios y guiones que se escriben dentro del documento (43.567.890) y no se guardan
	private static final Pattern DOCUMENT_SEPARATORS = Pattern.compile("[\\s.-]");
	// Caracteres especiales de LIKE (incluido el de escape) que se buscan como texto literal
	private static final Pattern LIKE_SPECIAL = Pattern.compile("[!%_]");

	// Campos por los que el front puede ordenar el listado; cualquier otro responde 400
	private static final Map<String, String> SORTABLE = Map.of(
			"receivedAt", "receivedAt",
			"estimatedDeliveryDate", "estimatedDeliveryDate",
			"estimatedDeliveryTime", "estimatedDeliveryTime",
			"status", "status",
			"readyAt", "readyAt",
			"deliveredAt", "deliveredAt",
			"id", "id");

	private final InterventionRepository interventionRepository;
	private final InterventionFindingRepository findingRepository;
	private final InterventionStatusHistoryRepository historyRepository;
	private final DeliveryDateChangeRepository deliveryDateChangeRepository;
	private final InterventionPricingService pricingService;
	private final InterventionSparePartService sparePartService;
	private final PaymentService paymentService;
	private final PersonService personService;
	private final DeviceService deviceService;
	private final SettingService settingService;
	private final PasswordEncoder passwordEncoder;
	private final InterventionMapper interventionMapper;
	private final Clock clock;
	private final ApplicationEventPublisher eventPublisher;
	private final InterventionEventPublisher interventionEvents;

	// Recibe el equipo: cliente, equipo, orden, servicio inicial e historial en una sola transacción
	@Override
	@Transactional
	public CreatedInterventionResponse create(CreateInterventionRequest request, AuthenticatedUser actor) {
		requireFrontDesk(actor, "Solo el asesor puede recibir equipos");
		requireFutureDelivery(request.estimatedDeliveryDate(), request.estimatedDeliveryTime());

		// 1. Cliente y equipo: se reutilizan si ya existen
		Person customer = personService.registerOrUpdateCustomer(request.customer());
		Device device = deviceService.registerOrReuse(customer, request.device());
		Person advisor = personService.getEntity(actor.id());
		Person technician = request.technicianId() == null ? null : assignableTechnician(request.technicianId());

		// 2. Clave de consulta: últimos 4 dígitos del celular o 4 al azar; solo se guarda su hash
		String accessKey = customer.getPhone() != null
				? customer.getPhone().substring(customer.getPhone().length() - 4)
				: randomAccessKey();

		// 3. Orden en RECEIVED
		Intervention intervention = interventionRepository.save(Intervention.builder()
				.device(device)
				.advisor(advisor)
				.technician(technician)
				.accessKeyHash(passwordEncoder.encode(accessKey))
				.problemDescription(request.problemDescription().trim())
				.physicalCondition(request.physicalCondition().trim())
				.unlockCode(blankToNull(request.unlockCode()))
				.estimatedPrice(money(request.estimatedPrice()))
				.estimatedDeliveryDate(request.estimatedDeliveryDate())
				.estimatedDeliveryTime(request.estimatedDeliveryTime())
				.build());

		// 4. Cada daño reportado como hallazgo inicial aprobado (sin repetir) y primer registro del historial
		findingRepository.saveAll(request.serviceTypes().stream()
				.distinct()
				.map(serviceType -> InterventionFinding.builder()
						.intervention(intervention)
						.serviceType(serviceType)
						.description(intervention.getProblemDescription())
						.origin(FindingOrigin.INITIAL)
						.approvalStatus(ApprovalStatus.APPROVED)
						.reportedBy(advisor)
						.build())
				.toList());
		recordStatusChange(intervention, null, InterventionStatus.RECEIVED, advisor, "Ingreso del equipo");

		String warning = customer.getPhone() == null && customer.getEmail() == null ? NO_CONTACT_WARNING : null;
		return new CreatedInterventionResponse(toDetail(intervention, actor), accessKey, warning);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<InterventionSummaryResponse> search(InterventionFilter filter, Pageable pageable) {
		pageable = SortWhitelist.restrict(pageable, SORTABLE);
		// El código se busca mientras se escribe (FT-1, 00001); el documento por coincidencia parcial
		InterventionCode.Parsed code = null;
		if (filter.code() != null && !filter.code().isBlank()) {
			if (!InterventionCode.isPartial(filter.code())) {
				throw new BusinessRuleException("El código " + filter.code() + " no es válido; use el formato FT-00001");
			}
			code = InterventionCode.parsePartial(filter.code()).orElse(null);
		}
		String document = filter.documentNumber() == null ? ""
				: DOCUMENT_SEPARATORS.matcher(filter.documentNumber()).replaceAll("").toUpperCase(Locale.ROOT);
		InterventionFilter normalized = new InterventionFilter(filter.code(),
				document.isEmpty() ? null : "%" + LIKE_SPECIAL.matcher(document).replaceAll("!$0") + "%",
				filter.customerId(), filter.technicianId(), filter.status(), filter.receivedFrom(), filter.receivedTo(), filter.overdue(),
				filter.openOnly(), filter.inProgress(), filter.unclaimed());
		if (normalized.receivedFrom() != null && normalized.receivedTo() != null
				&& normalized.receivedFrom().isAfter(normalized.receivedTo())) {
			throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
		}
		LocalDateTime now = now();
		return PageResponse.from(interventionRepository
				.findAll(InterventionSpecifications.matching(normalized, code, now, unclaimedBefore()), pageable)
				.map(intervention -> interventionMapper.toSummary(intervention, now)));
	}

	// Listas desde hace más de UNCLAIMED_ALERT_DAYS días, las más antiguas primero, con el contacto del cliente
	@Override
	@Transactional(readOnly = true)
	public List<UnclaimedOrderResponse> findUnclaimed() {
		LocalDateTime now = now();
		InterventionFilter unclaimed = new InterventionFilter(null, null, null, null, null, null, null, null, false, false, true);
		return interventionRepository
				.findAll(InterventionSpecifications.matching(unclaimed, null, now, unclaimedBefore()),
						Sort.by("readyAt", "id"))
				.stream()
				.map(intervention -> interventionMapper.toUnclaimed(intervention, now))
				.toList();
	}

	// Entregadas hoy en Bogotá, la más reciente primero; la Salida las muestra aunque se recargue la página
	@Override
	@Transactional(readOnly = true)
	public List<DeliveredOrderResponse> findDeliveredToday() {
		LocalDate today = today();
		return interventionRepository
				.findAll(InterventionSpecifications.deliveredBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
						Sort.by(Sort.Direction.DESC, "deliveredAt"))
				.stream()
				.map(interventionMapper::toDelivered)
				.toList();
	}

	// Garantía: solo sobre una orden original entregada con garantía vigente; se numera con la original bloqueada
	@Override
	@Transactional
	public InterventionDetailResponse openWarranty(Long originalId, WarrantyRequest request, AuthenticatedUser actor) {
		requireFrontDesk(actor, "Solo el asesor abre garantías");
		interventionRepository.findForUpdate(originalId)
				.orElseThrow(() -> new NotFoundException("No existe la orden con id " + originalId));
		Intervention original = findIntervention(originalId);
		String code = InterventionCode.of(original);

		// 1. La original: no es garantía, está entregada y su garantía sigue vigente
		if (original.getWarrantyOf() != null) {
			throw new ConflictException("No se abre una garantía sobre otra garantía; ábrala sobre la orden original "
					+ InterventionCode.of(original.getWarrantyOf()));
		}
		if (original.getStatus() != InterventionStatus.DELIVERED) {
			throw new ConflictException("Solo se abre garantía sobre una orden entregada; " + code + " está en "
					+ original.getStatus());
		}
		LocalDate until = original.getWarrantyUntil();
		if (until == null) {
			throw new ConflictException("La orden " + code + " se entregó sin garantía");
		}
		if (today().isAfter(until)) {
			throw new ConflictException("La garantía de " + code + " venció el " + until);
		}
		requireFutureDelivery(request.estimatedDeliveryDate(), request.estimatedDeliveryTime());

		// 2. Contacto del cliente con la regla de «No tiene»; lo que no llega se conserva
		Person customer = original.getDevice().getOwner();
		personService.registerOrUpdateCustomer(new CustomerRequest(customer.getDocumentType(),
				customer.getDocumentNumber(), null, request.phone(), request.noPhone(), request.email(),
				request.noEmail()));

		// 3. Garantía numerada, sin cobro y con la clave de consulta de la original
		Person advisor = personService.getEntity(actor.id());
		Person technician = request.technicianId() == null ? null : assignableTechnician(request.technicianId());
		int number = interventionRepository.findMaxWarrantyNumber(originalId) + 1;
		Intervention warranty = interventionRepository.save(Intervention.builder()
				.warrantyOf(original)
				.warrantyNumber(number)
				.device(original.getDevice())
				.advisor(advisor)
				.technician(technician)
				.problemDescription(request.problemDescription().trim())
				.physicalCondition(request.physicalCondition().trim())
				.unlockCode(blankToNull(request.unlockCode()))
				.estimatedPrice(BigDecimal.ZERO.setScale(2))
				.estimatedDeliveryDate(request.estimatedDeliveryDate())
				.estimatedDeliveryTime(request.estimatedDeliveryTime())
				.build());

		// 4. Servicio reportado e historial, igual que un ingreso
		findingRepository.save(InterventionFinding.builder()
				.intervention(warranty)
				.serviceType(request.serviceType())
				.description(warranty.getProblemDescription())
				.origin(FindingOrigin.INITIAL)
				.approvalStatus(ApprovalStatus.APPROVED)
				.reportedBy(advisor)
				.build());
		recordStatusChange(warranty, null, InterventionStatus.RECEIVED, advisor,
				"Ingreso por garantía " + number + " de la orden " + code + " (vigente hasta " + until + ")");
		return toDetail(warranty, actor);
	}

	// Cola de trabajo del técnico: por defecto sus órdenes abiertas
	@Override
	@Transactional(readOnly = true)
	public PageResponse<InterventionSummaryResponse> findAssignedTo(AuthenticatedUser actor, InterventionFilter filter,
			Pageable pageable) {
		pageable = SortWhitelist.restrict(pageable, SORTABLE);
		if (actor.role() != Role.TECHNICIAN) {
			throw new ForbiddenException("Solo los técnicos tienen órdenes asignadas");
		}
		return search(InterventionFilter.ofTechnician(actor.id(), filter.status(), filter.overdue(),
				filter.inProgress()), pageable);
	}

	// Salida: el total y lo abonado de todas las órdenes listas se calculan con dos consultas agregadas
	@Override
	@Transactional(readOnly = true)
	public List<ReadyForDeliveryResponse> findReadyForDelivery() {
		LocalDate today = today();
		InterventionFilter ready = new InterventionFilter(null, null, null, null, InterventionStatus.READY, null, null, null,
				false, false, false);
		List<Intervention> orders = interventionRepository.findAll(
				InterventionSpecifications.matching(ready, null, now(), unclaimedBefore()),
				Sort.by("readyAt", "id"));
		List<Long> ids = orders.stream().map(Intervention::getId).toList();
		Map<Long, BigDecimal> totals = pricingService.calculateTotals(orders);
		Map<Long, BigDecimal> paid = paymentService.netPaidByIntervention(ids);
		return orders.stream().map(order -> {
			BigDecimal total = totals.get(order.getId());
			BigDecimal orderPaid = paid.getOrDefault(order.getId(), BigDecimal.ZERO.setScale(2));
			BigDecimal difference = total.subtract(orderPaid);
			return new ReadyForDeliveryResponse(interventionMapper.toSummary(order, now()), total, orderPaid,
					difference.max(BigDecimal.ZERO).setScale(2), difference.negate().max(BigDecimal.ZERO).setScale(2));
		}).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public InterventionDetailResponse findById(Long id, AuthenticatedUser actor) {
		return toDetail(findVisibleIntervention(id, actor), actor);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StatusHistoryResponse> findHistory(Long id, AuthenticatedUser actor) {
		findVisibleIntervention(id, actor);
		return historyRepository.findByInterventionIdOrderByChangedAtAscIdAsc(id).stream()
				.map(interventionMapper::toHistory)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<DeliveryDateChangeResponse> findDeliveryChanges(Long id, AuthenticatedUser actor) {
		findVisibleIntervention(id, actor);
		return deliveryDateChangeRepository.findByInterventionIdOrderByChangedAtDescIdDesc(id).stream()
				.map(interventionMapper::toDeliveryChange)
				.toList();
	}

	@Override
	@Transactional
	public InterventionDetailResponse assignTechnician(Long id, Long technicianId, AuthenticatedUser actor) {
		requireFrontDesk(actor, "Solo el asesor puede asignar el técnico");
		Intervention intervention = findOpenIntervention(id);
		Person previousTechnician = intervention.getTechnician();
		intervention.setTechnician(assignableTechnician(technicianId));
		interventionEvents.technicianChanged(intervention, previousTechnician);
		return toDetail(intervention, actor);
	}

	// Reprograma la entrega y deja registro de quién, cuándo, la fecha y hora anterior, la nueva y el motivo
	@Override
	@Transactional
	public InterventionDetailResponse rescheduleDelivery(Long id, RescheduleDeliveryRequest request,
			AuthenticatedUser actor) {
		Intervention intervention = findOpenIntervention(id);
		if (!isFrontDesk(actor) && !isAssignedTechnician(intervention, actor)) {
			throw new ForbiddenException("Solo el asesor o el técnico asignado pueden reprogramar la entrega");
		}
		if (!request.newDate().atTime(request.newTime()).isAfter(now())) {
			throw new BusinessRuleException("La nueva fecha y hora de entrega debe ser posterior a este momento");
		}
		if (request.newDate().equals(intervention.getEstimatedDeliveryDate())
				&& request.newTime().equals(intervention.getEstimatedDeliveryTime())) {
			throw new BusinessRuleException("La nueva fecha y hora es igual a la entrega estimada actual");
		}

		deliveryDateChangeRepository.save(DeliveryDateChange.builder()
				.intervention(intervention)
				.previousDate(intervention.getEstimatedDeliveryDate())
				.previousTime(intervention.getEstimatedDeliveryTime())
				.newDate(request.newDate())
				.newTime(request.newTime())
				.reason(request.reason().trim())
				.changedBy(personService.getEntity(actor.id()))
				.build());
		intervention.setEstimatedDeliveryDate(request.newDate());
		intervention.setEstimatedDeliveryTime(request.newTime());
		interventionEvents.rescheduled(intervention);
		return toDetail(intervention, actor);
	}

	@Override
	@Transactional
	public InterventionDetailResponse registerDiagnosis(Long id, DiagnosisRequest request, AuthenticatedUser actor) {
		Intervention intervention = findOpenIntervention(id);
		requireRepairer(intervention, actor, "Solo el técnico asignado puede registrar el diagnóstico");
		String diagnosis = blankToNull(request.diagnosis());
		String solution = blankToNull(request.solution());
		if (diagnosis == null && solution == null) {
			throw new BusinessRuleException("Envíe el diagnóstico, la solución o ambos");
		}
		if (diagnosis != null) {
			intervention.setDiagnosis(diagnosis);
		}
		if (solution != null) {
			intervention.setSolution(solution);
		}
		interventionEvents.updated(intervention);
		return toDetail(intervention, actor);
	}

	// Daño adicional: queda aprobado si el total cabe en el presupuesto; si no, pendiente de la respuesta del cliente
	@Override
	@Transactional
	public InterventionDetailResponse addFinding(Long id, AdditionalFindingRequest request, AuthenticatedUser actor) {
		Intervention intervention = findWorkableIntervention(id, actor, "Solo el técnico asignado puede agregar daños");
		findingRepository.save(InterventionFinding.builder()
				.intervention(intervention)
				.serviceType(request.serviceType())
				.description(request.description().trim())
				.origin(FindingOrigin.ADDITIONAL)
				.approvalStatus(ApprovalStatus.APPROVED)
				.reportedBy(personService.getEntity(actor.id()))
				.build());
		findingRepository.flush();
		pricingService.reviewAdditionalFindings(intervention);
		interventionEvents.updated(intervention);
		return toDetail(intervention, actor);
	}

	@Override
	@Transactional
	public InterventionDetailResponse setLaborCost(Long id, LaborCostRequest request, AuthenticatedUser actor) {
		Intervention intervention = findWorkableIntervention(id, actor,
				"Solo el técnico asignado puede definir la mano de obra");
		BigDecimal laborCost = money(request.laborCost());
		if (intervention.getWarrantyOf() != null && laborCost.signum() > 0) {
			throw new BusinessRuleException("En una garantía la mano de obra es 0");
		}
		intervention.setLaborCost(laborCost);
		interventionRepository.flush();
		pricingService.reviewAdditionalFindings(intervention);
		interventionEvents.updated(intervention);
		return toDetail(intervention, actor);
	}

	// Respuesta del cliente a un daño adicional pendiente; un daño rechazado no se repara
	@Override
	@Transactional
	public InterventionDetailResponse answerFinding(Long id, Long findingId, FindingApprovalRequest request,
			AuthenticatedUser actor) {
		requireFrontDesk(actor, "Solo el asesor registra la respuesta del cliente");
		if (request.decision() == ApprovalStatus.PENDING) {
			throw new BusinessRuleException("La respuesta del cliente debe ser APPROVED o REJECTED");
		}
		Intervention intervention = findOpenIntervention(id);
		InterventionFinding finding = findingRepository.findByIdAndInterventionId(findingId, id)
				.orElseThrow(() -> new NotFoundException("La orden no tiene el daño con id " + findingId));
		if (finding.getApprovalStatus() != ApprovalStatus.PENDING) {
			throw new ConflictException("El daño no está pendiente de la respuesta del cliente");
		}
		finding.setApprovalStatus(request.decision());
		finding.setRespondedBy(personService.getEntity(actor.id()));
		finding.setRespondedAt(LocalDateTime.now(clock));
		interventionEvents.updated(intervention);
		return toDetail(intervention, actor);
	}

	// Resultado de la orden; sin reparación se cobra solo el diagnóstico, los repuestos vuelven y no hay garantía
	@Override
	@Transactional
	public InterventionDetailResponse registerOutcome(Long id, OutcomeRequest request, AuthenticatedUser actor) {
		Intervention intervention = findWorkableIntervention(id, actor,
				"Solo el técnico asignado puede marcar el resultado");
		RepairOutcome outcome = request.outcome();
		String reason = blankToNull(request.reason());
		Integer warrantyDays = request.warrantyDays();
		boolean isWarranty = intervention.getWarrantyOf() != null;

		if (outcome == RepairOutcome.REPAIRED) {
			// 1a. Reparada: se conserva la mano de obra y la garantía usa la de la tienda si no se indica otra
			if (intervention.getStatus() != InterventionStatus.IN_REPAIR) {
				throw new ConflictException("Pase la orden a reparación antes de marcarla como reparada");
			}
			if (request.diagnosisFee() != null) {
				throw new BusinessRuleException("El valor del diagnóstico solo aplica cuando la orden no se repara");
			}
			if (isWarranty && warrantyDays != null && warrantyDays > 0) {
				throw new BusinessRuleException("Una garantía no extiende la garantía de la orden original");
			}
			intervention.setWarrantyDays(isWarranty ? 0
					: warrantyDays != null ? warrantyDays : settingService.getIntValue(SettingKey.DEFAULT_WARRANTY_DAYS));
		} else {
			// 1b. No reparable o rechazada: motivo obligatorio, mano de obra = diagnóstico y sin garantía
			if (reason == null) {
				throw new BusinessRuleException("Indique el motivo por el que la orden no se repara");
			}
			if (request.diagnosisFee() == null) {
				throw new BusinessRuleException("Indique el valor del diagnóstico (puede ser 0)");
			}
			if (warrantyDays != null && warrantyDays > 0) {
				throw new BusinessRuleException("Una orden sin reparación no tiene garantía");
			}
			BigDecimal diagnosisFee = money(request.diagnosisFee());
			if (isWarranty && diagnosisFee.signum() > 0) {
				throw new BusinessRuleException("En una garantía el diagnóstico es 0");
			}

			// 2. Los repuestos no usados vuelven al inventario y los daños sin respuesta quedan rechazados
			sparePartService.returnAllToStock(intervention, personService.getEntity(actor.id()));
			findingRepository.findByInterventionIdAndOriginAndRespondedByIsNull(id, FindingOrigin.ADDITIONAL)
					.forEach(finding -> finding.setApprovalStatus(ApprovalStatus.REJECTED));
			intervention.setLaborCost(diagnosisFee);
			intervention.setWarrantyDays(0);
		}
		intervention.setRepairOutcome(outcome);
		intervention.setOutcomeReason(reason);
		interventionEvents.updated(intervention);
		return toDetail(intervention, actor);
	}

	// Cambia el estado solo por transiciones válidas y guarda el historial; la entrega y la cancelación van aparte
	@Override
	@Transactional
	public InterventionDetailResponse changeStatus(Long id, StatusChangeRequest request, AuthenticatedUser actor) {
		Intervention intervention = findIntervention(id);
		InterventionStatus current = intervention.getStatus();
		InterventionStatus next = request.status();
		if (next == InterventionStatus.DELIVERED || next == InterventionStatus.CANCELLED) {
			throw new ConflictException(next == InterventionStatus.DELIVERED
					? "La entrega se registra con la acción de entregar, que verifica los pagos"
					: "La cancelación se registra con la acción de cancelar");
		}
		if (!current.canChangeTo(next, intervention.getRepairOutcome())) {
			throw new ConflictException("No se puede pasar una orden de " + current + " a " + next);
		}
		requireRepairer(intervention, actor, "Solo el técnico asignado puede cambiar el estado de la orden");
		if (intervention.getTechnician() == null) {
			throw new ConflictException("Asigne un técnico antes de avanzar la orden");
		}
		if (next == InterventionStatus.READY) {
			requireReadyConditions(intervention);
			intervention.setReadyAt(LocalDateTime.now(clock));
		}

		intervention.setStatus(next);
		recordStatusChange(intervention, current, next, personService.getEntity(actor.id()), blankToNull(request.note()));
		return toDetail(intervention, actor);
	}

	// Entrega: exige pagos que cubran el total, lo guarda, borra la contraseña de desbloqueo y calcula la garantía
	@Override
	@Transactional
	public InterventionDetailResponse deliver(Long id, DeliveryRequest request, AuthenticatedUser actor) {
		requireFrontDesk(actor, "Solo el asesor puede entregar el equipo");
		Intervention intervention = findIntervention(id);
		if (intervention.getStatus() != InterventionStatus.READY) {
			throw new ConflictException("Solo se entregan órdenes listas para entregar; la orden "
					+ InterventionCode.of(intervention) + " está en " + intervention.getStatus());
		}

		// 1. Pagos contra el total
		BigDecimal total = pricingService.calculateTotal(intervention);
		BigDecimal missing = total.subtract(paymentService.netPaid(id));
		if (missing.signum() > 0) {
			throw new ConflictException("Faltan " + Money.format(missing) + " por cobrar para entregar la orden "
					+ InterventionCode.of(intervention));
		}

		// 2. Cierre de la orden y garantía desde el día de la entrega
		LocalDateTime now = LocalDateTime.now(clock);
		Integer warrantyDays = intervention.getWarrantyDays();
		intervention.setTotalPrice(total);
		intervention.setDeliveredAt(now);
		intervention.setUnlockCode(null);
		intervention.setWarrantyUntil(warrantyDays != null && warrantyDays > 0
				? now.toLocalDate().plusDays(warrantyDays) : null);
		String notes = request == null ? null : blankToNull(request.notes());
		intervention.setDeliveryNotes(notes);
		intervention.setStatus(InterventionStatus.DELIVERED);
		recordStatusChange(intervention, InterventionStatus.READY, InterventionStatus.DELIVERED,
				personService.getEntity(actor.id()), notes != null ? notes : "Entrega al cliente");
		return toDetail(intervention, actor);
	}

	// Cancelación: devuelve los repuestos, borra la contraseña de desbloqueo y deja anotado el abono por devolver
	@Override
	@Transactional
	public InterventionDetailResponse cancel(Long id, CancelRequest request, AuthenticatedUser actor) {
		requireFrontDesk(actor, "Solo el asesor puede cancelar la orden");
		Intervention intervention = findIntervention(id);
		InterventionStatus current = intervention.getStatus();
		if (current == InterventionStatus.DELIVERED) {
			throw new ConflictException("No se puede cancelar una orden entregada");
		}
		if (current == InterventionStatus.CANCELLED) {
			throw new ConflictException("La orden " + InterventionCode.of(intervention) + " ya está cancelada");
		}

		Person person = personService.getEntity(actor.id());
		sparePartService.returnAllToStock(intervention, person);
		intervention.setUnlockCode(null);
		intervention.setStatus(InterventionStatus.CANCELLED);

		BigDecimal refundDue = paymentService.netPaid(id);
		String note = request.reason().trim()
				+ (refundDue.signum() > 0 ? ". Abono por devolver: " + Money.format(refundDue) : "");
		recordStatusChange(intervention, current, InterventionStatus.CANCELLED, person, note);
		// Inventario cancela las solicitudes de repuestos que aún no se han pedido al proveedor
		eventPublisher.publishEvent(new InterventionCancelledEvent(id, actor.id()));
		return toDetail(intervention, actor);
	}

	// Nueva clave de consulta al reimprimir el comprobante; la anterior deja de servir y se desbloquea el portal
	@Override
	@Transactional
	public AccessKeyResponse regenerateAccessKey(Long id, AuthenticatedUser actor) {
		requireFrontDesk(actor, "Solo el asesor puede reimprimir el comprobante de ingreso");
		Intervention intervention = findIntervention(id);
		Intervention keyOwner = intervention.getWarrantyOf() != null ? intervention.getWarrantyOf() : intervention;
		String accessKey;
		do {
			accessKey = randomAccessKey();
		} while (keyOwner.getAccessKeyHash() != null && passwordEncoder.matches(accessKey, keyOwner.getAccessKeyHash()));
		keyOwner.setAccessKeyHash(passwordEncoder.encode(accessKey));
		keyOwner.setFailedLookupAttempts(0);
		keyOwner.setLookupLockedUntil(null);
		return new AccessKeyResponse(InterventionCode.of(keyOwner), accessKey);
	}

	@Override
	@Transactional
	public void markWaitingParts(Intervention intervention, Person actor, String note) {
		InterventionStatus current = intervention.getStatus();
		if (current == InterventionStatus.WAITING_PARTS) {
			return;
		}
		if (!current.canChangeTo(InterventionStatus.WAITING_PARTS, intervention.getRepairOutcome())) {
			throw new ConflictException("La orden " + InterventionCode.of(intervention)
					+ " debe estar en diagnóstico o en reparación para esperar repuestos");
		}
		intervention.setStatus(InterventionStatus.WAITING_PARTS);
		recordStatusChange(intervention, current, InterventionStatus.WAITING_PARTS, actor, note);
	}

	// Para pasar a lista: resultado marcado y ningún daño adicional esperando la respuesta del cliente
	private void requireReadyConditions(Intervention intervention) {
		if (intervention.getRepairOutcome() == null) {
			throw new ConflictException("Marque el resultado de la orden antes de pasarla a lista para entregar");
		}
		if (findingRepository.existsByInterventionIdAndApprovalStatus(intervention.getId(), ApprovalStatus.PENDING)) {
			throw new ConflictException("Hay daños adicionales pendientes de la respuesta del cliente");
		}
	}

	// Todo cambio de estado pasa por aquí: guarda el historial y avisa en tiempo real (creación o cambio de estado)
	private void recordStatusChange(Intervention intervention, InterventionStatus from, InterventionStatus to,
			Person changedBy, String note) {
		historyRepository.save(InterventionStatusHistory.builder()
				.intervention(intervention)
				.fromStatus(from)
				.toStatus(to)
				.changedBy(changedBy)
				.note(note)
				.build());
		if (from == null) {
			interventionEvents.created(intervention);
		} else {
			interventionEvents.statusChanged(intervention);
		}
	}

	private Person assignableTechnician(Long technicianId) {
		Person technician = personService.getActiveTechnician(technicianId);
		if (technician.getAvailability() == Availability.ABSENT) {
			throw new ConflictException("El técnico " + technician.getFullName() + " está ausente");
		}
		return technician;
	}

	private InterventionDetailResponse toDetail(Intervention intervention, AuthenticatedUser actor) {
		boolean showUnlockCode = isFrontDesk(actor) || isAssignedTechnician(intervention, actor);
		List<InterventionFinding> findings = findingRepository
				.findByInterventionIdOrderByCreatedAtAscIdAsc(intervention.getId());
		List<Intervention> warranties = intervention.getWarrantyOf() == null
				? interventionRepository.findByWarrantyOfIdOrderByWarrantyNumberAsc(intervention.getId())
				: List.of();
		return interventionMapper.toDetail(intervention, findings, showUnlockCode, actor.role() != Role.ADVISOR,
				charges(intervention), nextStatuses(intervention, findings), warranties, now());
	}

	private ChargesSummary charges(Intervention intervention) {
		BigDecimal total = pricingService.amountDue(intervention);
		BigDecimal paid = paymentService.netPaid(intervention.getId());
		BigDecimal difference = total.subtract(paid);
		return new ChargesSummary(total, paid, difference.max(BigDecimal.ZERO).setScale(2),
				difference.negate().max(BigDecimal.ZERO).setScale(2));
	}

	// Transiciones que se pueden hacer ya: lista para entregar solo con resultado y sin daños pendientes
	private List<InterventionStatus> nextStatuses(Intervention intervention, List<InterventionFinding> findings) {
		boolean readyAllowed = intervention.getRepairOutcome() != null
				&& findings.stream().noneMatch(finding -> finding.getApprovalStatus() == ApprovalStatus.PENDING);
		return Arrays.stream(InterventionStatus.values())
				.filter(next -> intervention.getStatus().canChangeTo(next, intervention.getRepairOutcome()))
				.filter(next -> next != InterventionStatus.READY || readyAllowed)
				.toList();
	}

	private Intervention findIntervention(Long id) {
		return pricingService.getEntity(id);
	}

	// El técnico solo consulta las órdenes asignadas a él; el asesor, el coordinador y el administrador, todas
	private Intervention findVisibleIntervention(Long id, AuthenticatedUser actor) {
		Intervention intervention = findIntervention(id);
		if (!isFrontDesk(actor) && !isAssignedTechnician(intervention, actor)) {
			throw new ForbiddenException("Solo puede consultar las órdenes asignadas a usted");
		}
		return intervention;
	}

	private Intervention findOpenIntervention(Long id) {
		Intervention intervention = findIntervention(id);
		if (intervention.getStatus().isClosed()) {
			throw new ConflictException("La orden " + InterventionCode.of(intervention) + " está "
					+ (intervention.getStatus() == InterventionStatus.DELIVERED ? "entregada" : "cancelada"));
		}
		return intervention;
	}

	// Orden en manos del técnico (diagnóstico, espera de repuestos o reparación) y no cerrada sin reparación
	private Intervention findWorkableIntervention(Long id, AuthenticatedUser actor, String forbiddenMessage) {
		Intervention intervention = findOpenIntervention(id);
		requireRepairer(intervention, actor, forbiddenMessage);
		String code = InterventionCode.of(intervention);
		if (!intervention.getStatus().isInProgress()) {
			throw new ConflictException("La orden " + code
					+ " debe estar en diagnóstico, esperando repuestos o en reparación");
		}
		if (intervention.getRepairOutcome() != null && intervention.getRepairOutcome().endsWithoutRepair()) {
			throw new ConflictException("La orden " + code + " terminó sin reparación y no se puede reabrir");
		}
		return intervention;
	}

	private void requireRepairer(Intervention intervention, AuthenticatedUser actor, String message) {
		if (!actor.isAdmin() && !isAssignedTechnician(intervention, actor)) {
			throw new ForbiddenException(message);
		}
	}

	private void requireFrontDesk(AuthenticatedUser actor, String message) {
		if (!isFrontDesk(actor)) {
			throw new ForbiddenException(message);
		}
	}

	private boolean isFrontDesk(AuthenticatedUser actor) {
		return FRONT_DESK.contains(actor.role());
	}

	private boolean isAssignedTechnician(Intervention intervention, AuthenticatedUser actor) {
		return intervention.getTechnician() != null && intervention.getTechnician().getId().equals(actor.id());
	}

	private String randomAccessKey() {
		return String.format("%04d", RANDOM.nextInt(10_000));
	}

	// Las órdenes listas desde antes de este momento cuentan como no reclamadas
	private LocalDateTime unclaimedBefore() {
		return LocalDateTime.now(clock).minusDays(settingService.getIntValue(SettingKey.UNCLAIMED_ALERT_DAYS));
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	// La entrega acordada debe quedar después del momento del ingreso (que es ahora); puede ser el mismo día
	private void requireFutureDelivery(LocalDate date, LocalTime time) {
		if (!date.atTime(time).isAfter(now())) {
			throw new BusinessRuleException("La fecha y hora estimada de entrega debe ser posterior al momento del ingreso");
		}
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
