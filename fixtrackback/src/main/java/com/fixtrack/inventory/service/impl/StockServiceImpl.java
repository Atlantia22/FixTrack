package com.fixtrack.inventory.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.ConflictException;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.mapper.InterventionCode;
import com.fixtrack.inventory.entity.PartRequest;
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.entity.StockMovement;
import com.fixtrack.inventory.entity.StockMovementType;
import com.fixtrack.inventory.repository.SparePartRepository;
import com.fixtrack.inventory.repository.StockMovementRepository;
import com.fixtrack.inventory.service.StockService;
import com.fixtrack.person.entity.Person;
import com.fixtrack.inventory.events.InventoryEventPublisher;

import lombok.RequiredArgsConstructor;

// Movimientos de stock por uso en órdenes, entradas de mercancía y compras de solicitudes; corre en la transacción de quien lo llama
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

	private final SparePartRepository sparePartRepository;
	private final StockMovementRepository stockMovementRepository;
	private final InventoryEventPublisher inventoryEvents;

	@Override
	@Transactional
	public SparePart consumeForRepair(Long sparePartId, int quantity, Intervention intervention, Person actor) {
		return consumeForRepair(sparePartId, quantity, null, intervention, actor);
	}

	@Override
	@Transactional
	public SparePart consumeForRepair(Long sparePartId, int quantity, BigDecimal unitCost, Intervention intervention,
			Person actor) {
		SparePart part = sparePartRepository.findByIdAndActiveTrue(sparePartId)
				.orElseThrow(() -> new NotFoundException("No existe el repuesto con id " + sparePartId));
		if (quantity > part.getStock()) {
			throw new ConflictException("Stock insuficiente de " + part.getName() + ": hay " + part.getStock()
					+ " disponibles y se pidieron " + quantity);
		}
		return applyMovement(part, -quantity, StockMovementType.OUT_REPAIR,
				unitCost != null ? unitCost : part.getCostPrice(), intervention, null, actor,
				"Uso en la orden " + InterventionCode.of(intervention)).getSparePart();
	}

	// El repuesto puede estar dado de baja: el stock vuelve igual, sin tocar sus datos de catálogo
	@Override
	@Transactional
	public SparePart returnFromRepair(Long sparePartId, int quantity, BigDecimal unitCost, Intervention intervention,
			Person actor) {
		SparePart part = sparePartRepository.findById(sparePartId)
				.orElseThrow(() -> new NotFoundException("No existe el repuesto con id " + sparePartId));
		return applyMovement(part, quantity, StockMovementType.RETURN, unitCost, intervention, null, actor,
				"Devolución de la orden " + InterventionCode.of(intervention)).getSparePart();
	}

	// El costo es el guardado en el consumo, para que el kárdex cuadre con el costo de la orden
	@Override
	@Transactional
	public SparePart correctRepairUsage(Long sparePartId, int difference, BigDecimal unitCost,
			Intervention intervention, Person actor) {
		SparePart part = sparePartRepository.findById(sparePartId)
				.orElseThrow(() -> new NotFoundException("No existe el repuesto con id " + sparePartId));
		if (difference > part.getStock()) {
			throw new ConflictException("Stock insuficiente de " + part.getName() + ": hay " + part.getStock()
					+ " disponibles y la corrección necesita " + difference + " más");
		}
		StockMovementType type = difference > 0 ? StockMovementType.OUT_REPAIR : StockMovementType.RETURN;
		return applyMovement(part, -difference, type, unitCost, intervention, null, actor,
				"Corrección de cantidad en la orden " + InterventionCode.of(intervention)).getSparePart();
	}

	@Override
	@Transactional
	public StockMovement registerPurchase(Long sparePartId, int quantity, BigDecimal unitCost, String reason,
			Person actor, PartRequest partRequest) {
		SparePart part = sparePartRepository.findByIdAndActiveTrue(sparePartId)
				.orElseThrow(() -> new NotFoundException("No existe el repuesto con id " + sparePartId));
		return applyMovement(part, quantity, StockMovementType.IN_PURCHASE, unitCost, null, partRequest, actor,
				reason);
	}

	// Actualiza el stock y lo escribe de inmediato para que el bloqueo optimista detecte otro uso simultáneo
	private StockMovement applyMovement(SparePart part, int quantity, StockMovementType type, BigDecimal unitCost,
			Intervention intervention, PartRequest partRequest, Person actor, String reason) {
		int previousStock = part.getStock();
		part.setStock(part.getStock() + quantity);
		SparePart saved = sparePartRepository.saveAndFlush(part);
		inventoryEvents.stockChanged(saved, previousStock);
		return stockMovementRepository.save(StockMovement.builder()
				.sparePart(saved)
				.type(type)
				.quantity(quantity)
				.balanceAfter(saved.getStock())
				.unitCost(unitCost)
				.intervention(intervention)
				.partRequest(partRequest)
				.reason(reason)
				.createdBy(actor)
				.build());
	}
}
