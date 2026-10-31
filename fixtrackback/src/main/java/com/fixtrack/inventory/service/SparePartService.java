package com.fixtrack.inventory.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.fixtrack.common.PageResponse;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.inventory.dto.CreateSparePartRequest;
import com.fixtrack.inventory.dto.NewSparePartRequest;
import com.fixtrack.inventory.dto.SparePartCandidateResponse;
import com.fixtrack.inventory.dto.SparePartFilter;
import com.fixtrack.inventory.dto.report.InventoryReport;
import com.fixtrack.inventory.dto.SparePartResponse;
import com.fixtrack.inventory.dto.StockEntryRequest;
import com.fixtrack.inventory.dto.StockEntryResponse;
import com.fixtrack.inventory.dto.UpdateSparePartRequest;
import com.fixtrack.inventory.entity.SparePart;

public interface SparePartService {

	PageResponse<SparePartResponse> search(SparePartFilter filter, Pageable pageable);

	InventoryReport inventoryReport(SparePartFilter filter);

	// Repuestos activos en el mínimo o agotados, para las alertas
	List<SparePartResponse> findLowStock();

	SparePartResponse findById(Long id);

	SparePartResponse create(CreateSparePartRequest request);

	// Repuesto que el técnico agrega al inventario desde una orden: sin stock y con SKU generado
	SparePart createForRequest(NewSparePartRequest request, Long deviceModelId);

	SparePartResponse update(Long id, UpdateSparePartRequest request);

	void deactivate(Long id);

	StockEntryResponse registerEntry(Long id, StockEntryRequest request, AuthenticatedUser actor);

	// Repuestos para elegir en una orden: primero los compatibles con el modelo del equipo
	List<SparePartCandidateResponse> findCandidates(Long partTypeId, Long deviceModelId, Long equipmentModelId);
}
