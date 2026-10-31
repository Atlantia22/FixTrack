package com.fixtrack.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.ForbiddenException;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.common.PageResponse;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.mapper.InterventionCode;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.intervention.service.InterventionService;
import com.fixtrack.intervention.service.InterventionSparePartService;
import com.fixtrack.inventory.dto.ApprovePartRequestRequest;
import com.fixtrack.inventory.dto.CreatePartRequestRequest;
import com.fixtrack.inventory.dto.OrderPartRequestRequest;
import com.fixtrack.inventory.dto.PartReceiptResponse;
import com.fixtrack.inventory.dto.PartRequestResponse;
import com.fixtrack.inventory.dto.ReceivePartRequestRequest;
import com.fixtrack.inventory.dto.RejectPartRequestRequest;
import com.fixtrack.inventory.entity.PartRequest;
import com.fixtrack.inventory.entity.PartRequestStatus;
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.entity.StockMovement;
import com.fixtrack.inventory.entity.Supplier;
import com.fixtrack.inventory.entity.SupplierSparePart;
import com.fixtrack.inventory.entity.SupplierSparePartId;
import com.fixtrack.inventory.mapper.PartRequestMapper;
import com.fixtrack.inventory.repository.PartRequestRepository;
import com.fixtrack.inventory.repository.SparePartRepository;
import com.fixtrack.inventory.repository.SupplierRepository;
import com.fixtrack.inventory.repository.SupplierSparePartRepository;
import com.fixtrack.inventory.service.PartRequestService;
import com.fixtrack.inventory.service.SparePartService;
import com.fixtrack.inventory.service.StockService;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.service.PersonService;

import lombok.RequiredArgsConstructor;

// Solicitudes de repuestos que no hay: el técnico las pide; el coordinador las aprueba, pide al proveedor y recibe
@Service
@RequiredArgsConstructor
public class PartRequestServiceImpl implements PartRequestService {

	private static final Set<PartRequestStatus> NOT_ORDERED = EnumSet.of(PartRequestStatus.PENDING,
			PartRequestStatus.APPROVED);
	private static final Set<InterventionStatus> WORKING = EnumSet.of(InterventionStatus.IN_DIAGNOSIS,
			InterventionStatus.WAITING_PARTS, InterventionStatus.IN_REPAIR);
	private static final Set<Role> FRONT_DESK = EnumSet.of(Role.ADVISOR, Role.COORDINATOR, Role.ADMIN);

	private final PartRequestRepository partRequestRepository;
	private final SparePartRepository sparePartRepository;
	private final SupplierRepository supplierRepository;
	private final SupplierSparePartRepository supplierSparePartRepository;
	private final InterventionPricingService interventionLookup;
	private final InterventionService interventionService;
	private final InterventionSparePartService interventionSparePartService;
	private final StockService stockService;
	private final SparePartService sparePartService;
	private final PersonService personService;
	private final PartRequestMapper partRequestMapper;
	private final Clock clock;

	// El técnico asignado pide lo que no hay en stock; la orden pasa a esperar repuestos
	@Override
	@Transactional
	public PartRequestResponse create(Long interventionId, CreatePartRequestRequest request, AuthenticatedUser actor) {
		Intervention intervention = interventionLookup.getEntity(interventionId);
		String code = InterventionCode.of(intervention);
		if (!actor.isAdmin() && !isAssignedTechnician(intervention, actor)) {
			throw new ForbiddenException("Solo el técnico asignado puede solicitar repuestos para la orden");
		}
		if (!WORKING.contains(intervention.getStatus()) || endsWithoutRepair(intervention)) {
			throw new ConflictException("La orden " + code + " debe estar en diagnóstico, esperando repuestos o en reparación");
		}

		// 1. Del catálogo (solo si no alcanza el stock), nuevo para el inventario o descrito a mano
		SparePart part = null;
		String description = blankToNull(request.description());
		if (request.newPart() != null) {
			if (request.sparePartId() != null) {
				throw new BusinessRuleException("Elija un repuesto del catálogo o agregue uno nuevo, no ambos");
			}
			part = sparePartService.createForRequest(request.newPart(), intervention.getDevice().getDeviceModel().getId());
			if (description == null) {
				description = part.getName();
			}
		} else if (request.sparePartId() != null) {
			part = findActivePart(request.sparePartId());
			if (part.getStock() >= request.quantity()) {
				throw new ConflictException("Hay " + part.getStock() + " de " + part.getName()
						+ " en inventario; regístrelo como repuesto usado en lugar de solicitarlo");
			}
			if (description == null) {
				description = part.getName();
			}
		} else if (description == null) {
			throw new BusinessRuleException("Elija el repuesto del catálogo o describa el que necesita");
		}

		// 2. Solicitud pendiente y orden en espera de repuestos
		Person technician = personService.getEntity(actor.id());
		PartRequest saved = partRequestRepository.save(PartRequest.builder()
				.intervention(intervention)
				.requestedBy(technician)
				.sparePart(part)
				.description(description)
				.quantity(request.quantity())
				.estimatedCost(money(request.estimatedCost() != null || request.newPart() == null
						? request.estimatedCost() : request.newPart().costPrice()))
				.build());
		interventionService.markWaitingParts(intervention, technician,
				"Solicitud de repuesto #" + saved.getId() + ": " + description + " × " + saved.getQuantity());
		return toResponse(saved);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<PartRequestResponse> search(PartRequestStatus status, Pageable pageable) {
		var page = partRequestRepository.search(status, pageable);
		Map<Long, SupplierSparePart> preferred = preferredSuppliers(page.getContent());
		return PageResponse.from(page.map(request -> toResponse(request, preferred)));
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<PartRequestResponse> findMine(AuthenticatedUser actor, PartRequestStatus status,
			Pageable pageable) {
		var page = partRequestRepository.searchByTechnician(actor.id(), status, pageable);
		Map<Long, SupplierSparePart> preferred = preferredSuppliers(page.getContent());
		return PageResponse.from(page.map(request -> toResponse(request, preferred)));
	}

	@Override
	@Transactional(readOnly = true)
	public PartRequestResponse findById(Long id) {
		return toResponse(findRequest(id));
	}

	// El técnico solo ve las de sus órdenes; recepción y coordinación, todas
	@Override
	@Transactional(readOnly = true)
	public List<PartRequestResponse> findByIntervention(Long interventionId, AuthenticatedUser actor) {
		Intervention intervention = interventionLookup.getEntity(interventionId);
		if (!FRONT_DESK.contains(actor.role()) && !isAssignedTechnician(intervention, actor)) {
			throw new ForbiddenException("Solo puede consultar las órdenes asignadas a usted");
		}
		List<PartRequest> requests = partRequestRepository.findByInterventionIdOrderByRequestedAtDescIdDesc(interventionId);
		Map<Long, SupplierSparePart> preferred = preferredSuppliers(requests);
		return requests.stream().map(request -> toResponse(request, preferred)).toList();
	}

	@Override
	@Transactional
	public PartRequestResponse approve(Long id, ApprovePartRequestRequest request, AuthenticatedUser actor) {
		PartRequest partRequest = lockRequest(id);
		requireStatus(partRequest, Set.of(PartRequestStatus.PENDING), "aprobar");
		if (request != null && request.sparePartId() != null) {
			partRequest.setSparePart(findActivePart(request.sparePartId()));
		}
		partRequest.setStatus(PartRequestStatus.APPROVED);
		partRequest.setHandledBy(personService.getEntity(actor.id()));
		return toResponse(partRequest);
	}

	@Override
	@Transactional
	public PartRequestResponse reject(Long id, RejectPartRequestRequest request, AuthenticatedUser actor) {
		PartRequest partRequest = lockRequest(id);
		requireStatus(partRequest, NOT_ORDERED, "rechazar");
		partRequest.setStatus(PartRequestStatus.REJECTED);
		partRequest.setRejectionReason(request.reason().trim());
		partRequest.setHandledBy(personService.getEntity(actor.id()));
		return toResponse(partRequest);
	}

	// Se pide al proveedor elegido o, si no se elige, al preferido del repuesto
	@Override
	@Transactional
	public PartRequestResponse order(Long id, OrderPartRequestRequest request, AuthenticatedUser actor) {
		PartRequest partRequest = lockRequest(id);
		requireStatus(partRequest, Set.of(PartRequestStatus.APPROVED), "pedir");
		Long supplierId = request == null ? null : request.supplierId();
		Supplier supplier;
		if (supplierId != null) {
			supplier = supplierRepository.findById(supplierId)
					.orElseThrow(() -> new NotFoundException("No existe el proveedor con id " + supplierId));
		} else {
			supplier = preferredSuppliers(List.of(partRequest)).values().stream().findFirst()
					.map(SupplierSparePart::getSupplier)
					.orElseThrow(() -> new BusinessRuleException("Elija el proveedor: el repuesto no tiene un proveedor preferido"));
		}
		if (!supplier.isActive()) {
			throw new ConflictException("El proveedor " + supplier.getName() + " está dado de baja");
		}
		if (request != null && request.estimatedCost() != null) {
			partRequest.setEstimatedCost(money(request.estimatedCost()));
		}
		partRequest.setSupplier(supplier);
		partRequest.setStatus(PartRequestStatus.ORDERED);
		partRequest.setHandledBy(personService.getEntity(actor.id()));
		return toResponse(partRequest);
	}

	// Recepción en una sola transacción: entrada, consumo en la orden que lo pidió y último costo del proveedor
	@Override
	@Transactional
	public PartReceiptResponse receive(Long id, ReceivePartRequestRequest request, AuthenticatedUser actor) {
		PartRequest partRequest = lockRequest(id);
		requireStatus(partRequest, Set.of(PartRequestStatus.ORDERED), "recibir");
		if (request.sparePartId() != null) {
			partRequest.setSparePart(findActivePart(request.sparePartId()));
		}
		SparePart part = partRequest.getSparePart();
		if (part == null) {
			throw new BusinessRuleException("Asocie el repuesto del catálogo que llegó antes de recibir la solicitud");
		}
		Intervention intervention = partRequest.getIntervention();
		Person coordinator = personService.getEntity(actor.id());
		BigDecimal actualCost = money(request.actualCost());

		// 1. Entrada IN_PURCHASE ligada a la solicitud
		StockMovement entry = stockService.registerPurchase(part.getId(), partRequest.getQuantity(), actualCost,
				"Compra de la solicitud #" + id + " para la orden " + InterventionCode.of(intervention), coordinator,
				partRequest);

		// 2. Consumo OUT_REPAIR en la orden que lo pidió, para que no quede disponible para otra
		boolean consumed = interventionSparePartService.registerReceivedPart(intervention, part.getId(),
				partRequest.getQuantity(), actualCost, coordinator);

		// 3. Último costo del proveedor; si no estaba asociado al repuesto, queda asociado
		Supplier supplier = partRequest.getSupplier();
		SupplierSparePart link = supplierSparePartRepository
				.findById(new SupplierSparePartId(supplier.getId(), part.getId()))
				.orElseGet(() -> supplierSparePartRepository.save(new SupplierSparePart(supplier, part)));
		link.setLastCost(actualCost);

		// 4. Solicitud recibida
		partRequest.setActualCost(actualCost);
		partRequest.setReceivedAt(LocalDateTime.now(clock));
		partRequest.setStatus(PartRequestStatus.RECEIVED);
		partRequest.setHandledBy(coordinator);
		int stockAfter = consumed ? entry.getBalanceAfter() - partRequest.getQuantity() : entry.getBalanceAfter();
		return new PartReceiptResponse(toResponse(partRequest), consumed, stockAfter);
	}

	@Override
	@Transactional
	public int cancelNotOrdered(Long interventionId) {
		List<PartRequest> open = partRequestRepository.findByInterventionIdAndStatusIn(interventionId, NOT_ORDERED);
		open.forEach(request -> request.setStatus(PartRequestStatus.CANCELLED));
		return open.size();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PartRequestResponse> findAwaitingCoordinator(int limit) {
		List<PartRequest> requests = partRequestRepository.findByStatusInOrderByRequestedAtAscIdAsc(NOT_ORDERED,
				PageRequest.of(0, limit));
		Map<Long, SupplierSparePart> preferred = preferredSuppliers(requests);
		return requests.stream().map(request -> toResponse(request, preferred)).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public long countAwaitingCoordinator() {
		return partRequestRepository.countByStatusIn(NOT_ORDERED);
	}

	// Proveedor preferido (activo) de cada repuesto, en una sola consulta
	private Map<Long, SupplierSparePart> preferredSuppliers(List<PartRequest> requests) {
		List<Long> partIds = requests.stream()
				.map(PartRequest::getSparePart)
				.filter(Objects::nonNull)
				.map(SparePart::getId)
				.distinct()
				.toList();
		if (partIds.isEmpty()) {
			return Map.of();
		}
		return supplierSparePartRepository.findBySparePartIdInAndPreferredTrue(partIds).stream()
				.filter(link -> link.getSupplier().isActive())
				.collect(Collectors.toMap(link -> link.getSparePart().getId(), Function.identity(), (a, b) -> a));
	}

	private PartRequestResponse toResponse(PartRequest request) {
		return toResponse(request, preferredSuppliers(List.of(request)));
	}

	// La sugerencia solo tiene sentido mientras no se ha pedido
	private PartRequestResponse toResponse(PartRequest request, Map<Long, SupplierSparePart> preferred) {
		SupplierSparePart suggestion = NOT_ORDERED.contains(request.getStatus()) && request.getSparePart() != null
				? preferred.get(request.getSparePart().getId())
				: null;
		return partRequestMapper.toResponse(request, suggestion);
	}

	private void requireStatus(PartRequest request, Set<PartRequestStatus> allowed, String action) {
		if (!allowed.contains(request.getStatus())) {
			throw new ConflictException("No se puede " + action + " la solicitud #" + request.getId() + " porque está en "
					+ request.getStatus());
		}
	}

	private PartRequest lockRequest(Long id) {
		return partRequestRepository.findForUpdate(id)
				.orElseThrow(() -> new NotFoundException("No existe la solicitud de repuesto con id " + id));
	}

	private PartRequest findRequest(Long id) {
		return partRequestRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("No existe la solicitud de repuesto con id " + id));
	}

	private SparePart findActivePart(Long id) {
		return sparePartRepository.findByIdAndActiveTrue(id)
				.orElseThrow(() -> new NotFoundException("No existe el repuesto con id " + id));
	}

	private boolean isAssignedTechnician(Intervention intervention, AuthenticatedUser actor) {
		return intervention.getTechnician() != null && intervention.getTechnician().getId().equals(actor.id());
	}

	private boolean endsWithoutRepair(Intervention intervention) {
		return intervention.getRepairOutcome() != null && intervention.getRepairOutcome().endsWithoutRepair();
	}

	private BigDecimal money(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
