package com.fixtrack.inventory.service.impl;

import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.SortWhitelist;
import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.common.PageResponse;
import com.fixtrack.inventory.dto.SupplierRequest;
import com.fixtrack.inventory.dto.SupplierResponse;
import com.fixtrack.inventory.entity.Supplier;
import com.fixtrack.inventory.mapper.SupplierMapper;
import com.fixtrack.inventory.repository.SupplierRepository;
import com.fixtrack.inventory.service.SupplierService;

import lombok.RequiredArgsConstructor;

// Proveedores: NIT único (también frente a los dados de baja) y baja lógica para conservar su historial
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

	private static final Pattern TAX_ID = Pattern.compile("^\\d{5,15}(-\\d)?$");
	private static final Pattern LIKE_SPECIAL = Pattern.compile("[!%_]");

	// Campos por los que el front puede ordenar el listado; cualquier otro responde 400
	private static final Map<String, String> SORTABLE = Map.of(
			"name", "name",
			"taxId", "taxId",
			"id", "id");

	private final SupplierRepository supplierRepository;
	private final SupplierMapper supplierMapper;

	@Override
	@Transactional(readOnly = true)
	public PageResponse<SupplierResponse> search(String search, boolean active, Pageable pageable) {
		pageable = SortWhitelist.restrict(pageable, SORTABLE);
		String pattern = search == null || search.isBlank() ? null
				: "%" + LIKE_SPECIAL.matcher(search.trim()).replaceAll("!$0") + "%";
		return PageResponse.from(supplierRepository.search(pattern, active, pageable).map(supplierMapper::toResponse));
	}

	@Override
	@Transactional(readOnly = true)
	public SupplierResponse findById(Long id) {
		return supplierMapper.toResponse(findSupplier(id));
	}

	@Override
	@Transactional
	public SupplierResponse create(SupplierRequest request) {
		String taxId = normalizeTaxId(request.taxId());
		supplierRepository.findByTaxId(taxId).ifPresent(existing -> {
			throw duplicateTaxId(existing);
		});
		Supplier supplier = Supplier.builder().taxId(taxId).build();
		apply(supplier, request);
		return supplierMapper.toResponse(supplierRepository.save(supplier));
	}

	@Override
	@Transactional
	public SupplierResponse update(Long id, SupplierRequest request) {
		Supplier supplier = findSupplier(id);
		String taxId = normalizeTaxId(request.taxId());
		supplierRepository.findByTaxId(taxId)
				.filter(existing -> !existing.getId().equals(id))
				.ifPresent(existing -> {
					throw duplicateTaxId(existing);
				});
		supplier.setTaxId(taxId);
		apply(supplier, request);
		return supplierMapper.toResponse(supplier);
	}

	// Baja lógica: deja de ofrecerse para los repuestos, pero sus vínculos y su historial se conservan
	@Override
	@Transactional
	public void deactivate(Long id) {
		Supplier supplier = findSupplier(id);
		if (!supplier.isActive()) {
			throw new ConflictException("El proveedor " + supplier.getName() + " ya está dado de baja");
		}
		supplier.setActive(false);
	}

	private void apply(Supplier supplier, SupplierRequest request) {
		supplier.setName(request.name().trim());
		supplier.setContactName(blankToNull(request.contactName()));
		supplier.setPhone(blankToNull(request.phone()));
		supplier.setEmail(request.email() == null || request.email().isBlank() ? null
				: request.email().trim().toLowerCase(Locale.ROOT));
	}

	// Sin puntos ni espacios: 900.123.456-7 y 900123456-7 son el mismo NIT
	private String normalizeTaxId(String raw) {
		String taxId = raw.replaceAll("[.\\s]", "");
		if (!TAX_ID.matcher(taxId).matches()) {
			throw new BusinessRuleException("El NIT debe tener entre 5 y 15 dígitos y, si lo lleva, el dígito de verificación (900123456-7)");
		}
		return taxId;
	}

	private ConflictException duplicateTaxId(Supplier existing) {
		return new ConflictException("Ya existe un proveedor con el NIT " + existing.getTaxId() + ": "
				+ existing.getName() + (existing.isActive() ? "" : " (dado de baja)"));
	}

	private Supplier findSupplier(Long id) {
		return supplierRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("No existe el proveedor con id " + id));
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
