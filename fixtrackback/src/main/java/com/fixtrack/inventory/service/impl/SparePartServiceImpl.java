package com.fixtrack.inventory.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.common.PageResponse;
import com.fixtrack.device.entity.DeviceModel;
import com.fixtrack.device.service.DeviceModelService;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.inventory.dto.CreateSparePartRequest;
import com.fixtrack.inventory.dto.NewSparePartRequest;
import com.fixtrack.inventory.dto.SparePartCandidateResponse;
import com.fixtrack.inventory.dto.SparePartFilter;
import com.fixtrack.inventory.dto.SparePartResponse;
import com.fixtrack.inventory.dto.StockEntryRequest;
import com.fixtrack.inventory.dto.StockEntryResponse;
import com.fixtrack.inventory.dto.UpdateSparePartRequest;
import com.fixtrack.inventory.dto.report.InventoryReport;
import com.fixtrack.inventory.dto.report.InventoryReportItem;
import com.fixtrack.inventory.dto.report.InventoryTotals;
import com.fixtrack.inventory.entity.PartQuality;
import com.fixtrack.inventory.entity.PartType;
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.entity.StockMovement;
import com.fixtrack.inventory.entity.StockStatus;
import com.fixtrack.inventory.mapper.SparePartMapper;
import com.fixtrack.inventory.repository.PartTypeRepository;
import com.fixtrack.inventory.repository.SparePartCompatibilityRepository;
import com.fixtrack.inventory.repository.SparePartRepository;
import com.fixtrack.inventory.service.SparePartService;
import com.fixtrack.inventory.service.StockService;
import com.fixtrack.person.service.PersonService;
import com.fixtrack.setting.entity.SettingKey;
import com.fixtrack.setting.service.SettingService;

import lombok.RequiredArgsConstructor;

// Repuestos del inventario: SKU único, un solo repuesto por tipo, modelo y calidad, y baja lógica
@Service
@RequiredArgsConstructor
public class SparePartServiceImpl implements SparePartService {

	// Caracteres especiales de LIKE (incluido el de escape) que se buscan como texto literal
	private static final Pattern LIKE_SPECIAL = Pattern.compile("[!%_]");
	private static final Map<String, String> SORTABLE = Map.of(
			"name", "name",
			"sku", "sku",
			"stock", "stock",
			"minStock", "minStock",
			"salePrice", "salePrice",
			"costPrice", "costPrice",
			"partType", "partType.name",
			"deviceModel", "deviceModel.name",
			"brand", "deviceModel.brand.name");

	private final SparePartRepository sparePartRepository;
	private final SparePartCompatibilityRepository sparePartCompatibilityRepository;
	private final PartTypeRepository partTypeRepository;
	private final DeviceModelService deviceModelService;
	private final SettingService settingService;
	private final SparePartMapper sparePartMapper;
	private final StockService stockService;
	private final PersonService personService;

	// Búsqueda parcial sin mayúsculas ni tildes, filtros combinables y orden solo por los campos permitidos
	@Override
	@Transactional(readOnly = true)
	public PageResponse<SparePartResponse> search(SparePartFilter filter, Pageable pageable) {
		String pattern = likePattern(filter.search());
		Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toSort(pageable.getSort()));
		return PageResponse.from(sparePartRepository.search(pattern, filter.partTypeId(), filter.deviceModelId(),
				filter.brandId(), filter.quality(), filter.stockStatus() == null ? null : filter.stockStatus().name(),
				filter.alertsOnly(), sorted).map(sparePartMapper::toResponse));
	}

	// Existencias por repuesto, valor total a costo y artículos en alerta, todo agregado en la base de datos
	@Override
	@Transactional(readOnly = true)
	public InventoryReport inventoryReport(SparePartFilter filter) {
		String pattern = likePattern(filter.search());
		String stockStatus = filter.stockStatus() == null ? null : filter.stockStatus().name();
		List<InventoryReportItem> items = sparePartRepository
				.inventoryReport(pattern, filter.partTypeId(), filter.deviceModelId(), filter.brandId(),
						filter.quality(), stockStatus, filter.alertsOnly())
				.stream()
				.map(row -> new InventoryReportItem(row.id(), row.sku(), row.name(), row.partTypeName(),
						row.brandName() + " " + row.deviceModelName(), row.quality(), row.stock(), row.minStock(),
						StockStatus.of(row.stock(), row.minStock()), row.costPrice(),
						row.costValue().setScale(2, RoundingMode.HALF_UP)))
				.toList();
		InventoryTotals totals = sparePartRepository.inventoryTotals(pattern, filter.partTypeId(),
				filter.deviceModelId(), filter.brandId(), filter.quality(), stockStatus, filter.alertsOnly());
		return new InventoryReport(new InventoryTotals(totals.parts(), totals.units(),
				totals.costValue().setScale(2, RoundingMode.HALF_UP), totals.inAlert(), totals.outOfStock()), items);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SparePartResponse> findLowStock() {
		return sparePartRepository.findLowStock().stream().map(sparePartMapper::toResponse).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public SparePartResponse findById(Long id) {
		return sparePartMapper.toResponse(findActive(id));
	}

	// Un repuesto nuevo empieza sin stock: el stock solo cambia con movimientos de inventario
	@Override
	@Transactional
	public SparePartResponse create(CreateSparePartRequest request) {
		PartType partType = findPartType(request.partTypeId());
		DeviceModel deviceModel = deviceModelService.getEntity(request.deviceModelId());
		String sku = request.sku().trim();
		if (sparePartRepository.existsBySku(sku)) {
			throw duplicateSku(sku);
		}
		if (sparePartRepository.existsByPartTypeIdAndDeviceModelIdAndQuality(partType.getId(), deviceModel.getId(),
				request.quality())) {
			throw duplicateCombination(partType, deviceModel, request.quality());
		}

		int minStock = request.minStock() != null
				? request.minStock()
				: settingService.getIntValue(SettingKey.DEFAULT_MIN_STOCK);

		SparePart part = SparePart.builder()
				.name(request.name().trim())
				.description(blankToNull(request.description()))
				.sku(sku)
				.partType(partType)
				.deviceModel(deviceModel)
				.quality(request.quality())
				.minStock(minStock)
				.costPrice(money(request.costPrice()))
				.salePrice(money(request.salePrice()))
				.build();
		return sparePartMapper.toResponse(sparePartRepository.save(part));
	}

	// Repuesto nuevo pedido desde una orden: si ya existe la combinación tipo-modelo-calidad se elige ese, no se duplica
	@Override
	@Transactional
	public SparePart createForRequest(NewSparePartRequest request, Long deviceModelId) {
		PartType partType = findPartType(request.partTypeId());
		DeviceModel deviceModel = deviceModelService.getEntity(deviceModelId);
		sparePartRepository.findFirstByPartTypeIdAndDeviceModelIdAndQuality(partType.getId(), deviceModel.getId(),
				request.quality()).ifPresent(existing -> {
					throw new ConflictException("Ya existe «" + existing.getName() + "» (" + existing.getSku()
							+ ") en el inventario; elíjalo de la lista de repuestos");
				});

		return sparePartRepository.save(SparePart.builder()
				.name(request.name().trim())
				.sku(generateSku(partType, deviceModel, request.quality()))
				.partType(partType)
				.deviceModel(deviceModel)
				.quality(request.quality())
				.minStock(settingService.getIntValue(SettingKey.DEFAULT_MIN_STOCK))
				.costPrice(money(request.costPrice()))
				.salePrice(money(request.salePrice()))
				.build());
	}

	// Edición parcial: solo cambia lo que llega; vuelve a validar SKU y la combinación tipo-modelo-calidad
	@Override
	@Transactional
	public SparePartResponse update(Long id, UpdateSparePartRequest request) {
		SparePart part = findActive(id);

		if (request.sku() != null) {
			String sku = request.sku().trim();
			if (sparePartRepository.existsBySkuAndIdNot(sku, id)) {
				throw duplicateSku(sku);
			}
			part.setSku(sku);
		}

		PartType partType = request.partTypeId() != null ? findPartType(request.partTypeId()) : part.getPartType();
		DeviceModel deviceModel = request.deviceModelId() != null
				? deviceModelService.getEntity(request.deviceModelId())
				: part.getDeviceModel();
		PartQuality quality = request.quality() != null ? request.quality() : part.getQuality();
		boolean combinationChanged = !partType.getId().equals(part.getPartType().getId())
				|| !deviceModel.getId().equals(part.getDeviceModel().getId())
				|| !Objects.equals(quality, part.getQuality());
		if (combinationChanged && sparePartRepository.existsByPartTypeIdAndDeviceModelIdAndQualityAndIdNot(
				partType.getId(), deviceModel.getId(), quality, id)) {
			throw duplicateCombination(partType, deviceModel, quality);
		}
		part.setPartType(partType);
		part.setDeviceModel(deviceModel);
		part.setQuality(quality);

		if (request.name() != null) {
			part.setName(request.name().trim());
		}
		if (request.description() != null) {
			part.setDescription(blankToNull(request.description()));
		}
		if (request.minStock() != null) {
			part.setMinStock(request.minStock());
		}
		if (request.costPrice() != null) {
			part.setCostPrice(money(request.costPrice()));
		}
		if (request.salePrice() != null) {
			part.setSalePrice(money(request.salePrice()));
		}
		return sparePartMapper.toResponse(part);
	}

	// Baja lógica: sale de los listados pero conserva sus movimientos y su uso en órdenes
	@Override
	@Transactional
	public void deactivate(Long id) {
		findActive(id).setActive(false);
	}

	// Entrada de mercancía: el stock sube y queda el movimiento IN_PURCHASE con su costo, motivo y responsable
	@Override
	@Transactional
	public StockEntryResponse registerEntry(Long id, StockEntryRequest request, AuthenticatedUser actor) {
		StockMovement movement = stockService.registerPurchase(id, request.quantity(), money(request.unitCost()),
				request.reason().trim(), personService.getEntity(actor.id()), null);
		return new StockEntryResponse(sparePartMapper.toResponse(findActive(id)), sparePartMapper.toMovement(movement));
	}

	@Override
	@Transactional(readOnly = true)
	public List<SparePartCandidateResponse> findCandidates(Long partTypeId, Long deviceModelId, Long equipmentModelId) {
		Set<Long> compatibleIds = new HashSet<>(
				sparePartCompatibilityRepository.findSparePartIdsByDeviceModelId(equipmentModelId));
		return sparePartRepository.findCandidates(partTypeId, deviceModelId).stream()
				.map(part -> sparePartMapper.toCandidate(part,
						part.getDeviceModel().getId().equals(equipmentModelId) || compatibleIds.contains(part.getId())))
				.sorted(Comparator.comparing(SparePartCandidateResponse::compatible).reversed())
				.toList();
	}

	// Traduce el orden pedido por el front a propiedades de la entidad; el id desempata para paginar estable
	private Sort toSort(Sort requested) {
		List<Sort.Order> orders = new ArrayList<>();
		for (Sort.Order order : requested) {
			String property = SORTABLE.get(order.getProperty());
			if (property == null) {
				throw new BusinessRuleException("No se puede ordenar por " + order.getProperty() + "; use uno de "
						+ String.join(", ", new TreeSet<>(SORTABLE.keySet())));
			}
			orders.add(new Sort.Order(order.getDirection(), property));
		}
		if (orders.isEmpty()) {
			orders.add(Sort.Order.asc("name"));
		}
		orders.add(Sort.Order.asc("id"));
		return Sort.by(orders);
	}

	// Texto de búsqueda con los comodines de LIKE escapados, entre %
	private String likePattern(String search) {
		return search == null || search.isBlank() ? null
				: "%" + LIKE_SPECIAL.matcher(search.trim()).replaceAll("!$0") + "%";
	}

	private SparePart findActive(Long id) {
		return sparePartRepository.findByIdAndActiveTrue(id)
				.orElseThrow(() -> new NotFoundException("No existe el repuesto con id " + id));
	}

	private PartType findPartType(Long partTypeId) {
		return partTypeRepository.findById(partTypeId)
				.orElseThrow(() -> new NotFoundException("No existe el tipo de repuesto con id " + partTypeId));
	}

	// SKU con el formato del inventario: tipo, modelo y calidad («PAN-GALAXYA15-GEN»); si ya existe se numera
	private String generateSku(PartType partType, DeviceModel deviceModel, PartQuality quality) {
		String base = skuToken(partType.getName(), 3) + "-" + skuToken(deviceModel.getName(), 14)
				+ (quality == null ? "" : "-" + quality.name().substring(0, 3));
		String sku = base;
		for (int copy = 2; sparePartRepository.existsBySku(sku); copy++) {
			sku = base + "-" + copy;
		}
		return sku;
	}

	// Solo letras y números en mayúscula, sin tildes, recortado a maxLength
	private String skuToken(String text, int maxLength) {
		String token = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]", "")
				.toUpperCase(Locale.ROOT);
		return token.substring(0, Math.min(maxLength, token.length()));
	}

	private ConflictException duplicateSku(String sku) {
		return new ConflictException("Ya existe un repuesto con el SKU " + sku);
	}

	private ConflictException duplicateCombination(PartType partType, DeviceModel deviceModel, PartQuality quality) {
		String qualityText = quality == null ? "sin calidad" : "de calidad " + quality;
		return new ConflictException("Ya existe un repuesto de tipo " + partType.getName() + " para "
				+ deviceModel.getBrand().getName() + " " + deviceModel.getName() + " " + qualityText);
	}

	private BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
