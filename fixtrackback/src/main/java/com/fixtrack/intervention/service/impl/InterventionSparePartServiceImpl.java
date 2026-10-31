package com.fixtrack.intervention.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.ForbiddenException;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.dto.InterventionPartsResponse;
import com.fixtrack.intervention.dto.NewPartUsageRequest;
import com.fixtrack.intervention.dto.PersonRef;
import com.fixtrack.intervention.dto.SparePartUsageRequest;
import com.fixtrack.intervention.dto.SparePartUsageResponse;
import com.fixtrack.intervention.dto.UpdateSparePartUsageRequest;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionSparePart;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.mapper.InterventionCode;
import com.fixtrack.intervention.repository.InterventionSparePartRepository;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.intervention.service.InterventionSparePartService;
import com.fixtrack.inventory.dto.SparePartCandidateResponse;
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.service.SparePartService;
import com.fixtrack.inventory.service.StockService;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.service.PersonService;

import lombok.RequiredArgsConstructor;

// Repuestos usados en una orden: solo el técnico asignado los registra o corrige, siempre junto con el stock
@Service
@RequiredArgsConstructor
public class InterventionSparePartServiceImpl implements InterventionSparePartService {

	private static final Set<InterventionStatus> WORKING = EnumSet.of(InterventionStatus.IN_DIAGNOSIS,
			InterventionStatus.WAITING_PARTS, InterventionStatus.IN_REPAIR);

	private final InterventionSparePartRepository usageRepository;
	private final InterventionPricingService pricingService;
	private final SparePartService sparePartService;
	private final StockService stockService;
	private final PersonService personService;

	@Override
	@Transactional(readOnly = true)
	public InterventionPartsResponse findByIntervention(Long interventionId, AuthenticatedUser actor) {
		return toResponse(findVisibleIntervention(interventionId, actor), actor);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SparePartCandidateResponse> findCandidates(Long interventionId, Long partTypeId, Long deviceModelId,
			AuthenticatedUser actor) {
		Intervention intervention = findVisibleIntervention(interventionId, actor);
		return sparePartService.findCandidates(partTypeId, deviceModelId,
				intervention.getDevice().getDeviceModel().getId());
	}

	// Registra el consumo: guarda precio y costo del momento, descuenta stock y deja el movimiento OUT_REPAIR
	@Override
	@Transactional
	public InterventionPartsResponse register(Long interventionId, SparePartUsageRequest request,
			AuthenticatedUser actor) {
		Intervention intervention = findEditableIntervention(interventionId, actor);
		consume(intervention, request.sparePartId(), request.quantity(), personService.getEntity(actor.id()));
		return toResponse(intervention, actor);
	}

	// Repuesto conseguido por el técnico: alta en el inventario, entrada IN_PURCHASE con lo conseguido y consumo en la orden
	@Override
	@Transactional
	public InterventionPartsResponse registerNewPart(Long interventionId, NewPartUsageRequest request,
			AuthenticatedUser actor) {
		Intervention intervention = findEditableIntervention(interventionId, actor);
		if (request.quantityUsed() > request.quantityObtained()) {
			throw new BusinessRuleException("No puede usar más unidades de las que consiguió");
		}
		Person technician = personService.getEntity(actor.id());

		// 1. Repuesto nuevo para el modelo del equipo y entrada de las unidades conseguidas
		SparePart part = sparePartService.createForRequest(request.part(),
				intervention.getDevice().getDeviceModel().getId());
		stockService.registerPurchase(part.getId(), request.quantityObtained(), part.getCostPrice(),
				"Conseguido por el técnico para la orden " + InterventionCode.of(intervention), technician, null);

		// 2. Consumo en la orden; lo que sobre queda en el inventario
		consume(intervention, part.getId(), request.quantityUsed(), technician);
		return toResponse(intervention, actor);
	}

	// Descuenta el stock, guarda el consumo con los precios del momento y revisa los daños adicionales con el nuevo total
	private void consume(Intervention intervention, Long sparePartId, int quantity, Person technician) {
		// 1. Stock y movimiento; si no alcanza o hay otro uso simultáneo se revierte todo
		SparePart part = stockService.consumeForRepair(sparePartId, quantity, intervention, technician);

		// 2. Consumo con los precios del momento; en garantías el cliente no paga el repuesto
		BigDecimal unitPrice = intervention.getWarrantyOf() != null ? BigDecimal.ZERO : part.getSalePrice();
		usageRepository.save(InterventionSparePart.builder()
				.intervention(intervention)
				.sparePart(part)
				.quantity(quantity)
				.unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
				.costPrice(part.getCostPrice())
				.createdBy(technician)
				.build());

		// 3. Con el nuevo total, los daños adicionales pueden quedar pendientes de la respuesta del cliente
		pricingService.reviewAdditionalFindings(intervention);
	}

	// Corrige la cantidad: el stock se ajusta por la diferencia y el total se recalcula con el precio guardado del uso
	@Override
	@Transactional
	public InterventionPartsResponse correctQuantity(Long interventionId, Long usageId,
			UpdateSparePartUsageRequest request, AuthenticatedUser actor) {
		Intervention intervention = findEditableIntervention(interventionId, actor);
		InterventionSparePart usage = findUsage(interventionId, usageId);
		int difference = request.quantity() - usage.getQuantity();
		if (difference == 0) {
			throw new BusinessRuleException("La cantidad nueva es igual a la registrada");
		}

		stockService.correctRepairUsage(usage.getSparePart().getId(), difference, usage.getCostPrice(), intervention,
				personService.getEntity(actor.id()));
		usage.setQuantity(request.quantity());
		usageRepository.flush();
		pricingService.reviewAdditionalFindings(intervention);
		return toResponse(intervention, actor);
	}

	// Elimina un consumo: devuelve el stock con un movimiento RETURN y borra el registro
	@Override
	@Transactional
	public InterventionPartsResponse remove(Long interventionId, Long usageId, AuthenticatedUser actor) {
		Intervention intervention = findEditableIntervention(interventionId, actor);
		InterventionSparePart usage = findUsage(interventionId, usageId);

		stockService.returnFromRepair(usage.getSparePart().getId(), usage.getQuantity(), usage.getCostPrice(),
				intervention, personService.getEntity(actor.id()));
		usageRepository.delete(usage);
		usageRepository.flush();
		pricingService.reviewAdditionalFindings(intervention);
		return toResponse(intervention, actor);
	}

	@Override
	@Transactional(readOnly = true)
	public List<InterventionSparePart> findUsages(Long interventionId) {
		return usageRepository.findByInterventionIdOrderByCreatedAtAscIdAsc(interventionId);
	}

	@Override
	@Transactional
	public int returnAllToStock(Intervention intervention, Person actor) {
		List<InterventionSparePart> usages = findUsages(intervention.getId());
		int units = 0;
		for (InterventionSparePart usage : usages) {
			stockService.returnFromRepair(usage.getSparePart().getId(), usage.getQuantity(), usage.getCostPrice(),
					intervention, actor);
			units += usage.getQuantity();
		}
		usageRepository.deleteAll(usages);
		usageRepository.flush();
		return units;
	}

	// Solo se consume si la orden sigue en trabajo; si se cerró o terminó sin reparación, el repuesto queda en stock
	@Override
	@Transactional
	public boolean registerReceivedPart(Intervention intervention, Long sparePartId, int quantity, BigDecimal unitCost,
			Person actor) {
		boolean working = WORKING.contains(intervention.getStatus())
				&& (intervention.getRepairOutcome() == null || !intervention.getRepairOutcome().endsWithoutRepair());
		if (!working) {
			return false;
		}
		SparePart part = stockService.consumeForRepair(sparePartId, quantity, unitCost, intervention, actor);
		BigDecimal unitPrice = intervention.getWarrantyOf() != null ? BigDecimal.ZERO : part.getSalePrice();
		usageRepository.save(InterventionSparePart.builder()
				.intervention(intervention)
				.sparePart(part)
				.quantity(quantity)
				.unitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP))
				.costPrice(unitCost.setScale(2, RoundingMode.HALF_UP))
				.createdBy(actor)
				.build());
		usageRepository.flush();
		pricingService.reviewAdditionalFindings(intervention);
		return true;
	}

	// El asesor solo ve qué se usó y el total; el técnico (de sus órdenes), el coordinador y el administrador ven el
	// desglose, el costo de repuestos y la ganancia
	private InterventionPartsResponse toResponse(Intervention intervention, AuthenticatedUser actor) {
		boolean breakdown = actor.role() != Role.ADVISOR;
		List<InterventionSparePart> usages = findUsages(intervention.getId());
		BigDecimal partsTotal = usages.stream().map(this::subtotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
		BigDecimal laborCost = intervention.getLaborCost().setScale(2, RoundingMode.HALF_UP);
		BigDecimal total = laborCost.add(partsTotal);
		BigDecimal partsCost = usages.stream()
				.map(usage -> usage.getCostPrice().multiply(BigDecimal.valueOf(usage.getQuantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
		return new InterventionPartsResponse(
				usages.stream().map(usage -> toUsage(usage, breakdown)).toList(),
				breakdown ? partsTotal : null,
				breakdown ? laborCost : null,
				total,
				breakdown ? partsCost : null,
				breakdown ? total.subtract(partsCost) : null);
	}

	private SparePartUsageResponse toUsage(InterventionSparePart usage, boolean breakdown) {
		SparePart part = usage.getSparePart();
		return new SparePartUsageResponse(usage.getId(), part.getId(), part.getName(), part.getSku(),
				part.getQuality(), usage.getQuantity(), breakdown ? usage.getUnitPrice() : null,
				breakdown ? subtotal(usage) : null,
				new PersonRef(usage.getCreatedBy().getId(), usage.getCreatedBy().getFullName()), usage.getCreatedAt());
	}

	private BigDecimal subtotal(InterventionSparePart usage) {
		return usage.getUnitPrice().multiply(BigDecimal.valueOf(usage.getQuantity())).setScale(2, RoundingMode.HALF_UP);
	}

	private InterventionSparePart findUsage(Long interventionId, Long usageId) {
		return usageRepository.findByIdAndInterventionId(usageId, interventionId)
				.orElseThrow(() -> new NotFoundException("La orden no tiene el consumo con id " + usageId));
	}

	// El técnico solo consulta los repuestos de las órdenes asignadas a él
	private Intervention findVisibleIntervention(Long id, AuthenticatedUser actor) {
		Intervention intervention = pricingService.getEntity(id);
		if (actor.role() == Role.TECHNICIAN && !isAssignedTechnician(intervention, actor)) {
			throw new ForbiddenException("Solo puede consultar las órdenes asignadas a usted");
		}
		return intervention;
	}

	// Solo el técnico asignado, mientras la orden siga en trabajo y no se haya cerrado sin reparación
	private Intervention findEditableIntervention(Long id, AuthenticatedUser actor) {
		Intervention intervention = pricingService.getEntity(id);
		if (!isAssignedTechnician(intervention, actor)) {
			throw new ForbiddenException("Solo el técnico asignado puede registrar o corregir los repuestos usados");
		}
		String code = InterventionCode.of(intervention);
		if (intervention.getStatus().isClosed()) {
			throw new ConflictException("La orden " + code + " ya está cerrada");
		}
		if (intervention.getStatus() == InterventionStatus.READY) {
			throw new ConflictException("La orden " + code + " ya está lista para entregar; sus repuestos no cambian");
		}
		if (intervention.getRepairOutcome() != null && intervention.getRepairOutcome().endsWithoutRepair()) {
			throw new ConflictException("La orden " + code + " terminó sin reparación; no usa repuestos");
		}
		return intervention;
	}

	private boolean isAssignedTechnician(Intervention intervention, AuthenticatedUser actor) {
		return intervention.getTechnician() != null && intervention.getTechnician().getId().equals(actor.id());
	}
}
