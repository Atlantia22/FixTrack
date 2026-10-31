package com.fixtrack.inventory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fixtrack.inventory.dto.report.InventoryReportRow;
import com.fixtrack.inventory.dto.report.InventoryTotals;
import com.fixtrack.inventory.entity.PartQuality;
import com.fixtrack.inventory.entity.SparePart;

public interface SparePartRepository extends JpaRepository<SparePart, Long> {

	Optional<SparePart> findBySku(String sku);

	boolean existsBySku(String sku);

	boolean existsBySkuAndIdNot(String sku, Long id);

	// Con calidad null genera IS NULL, así detecta duplicados sin calidad que MySQL no bloquea
	boolean existsByPartTypeIdAndDeviceModelIdAndQuality(Long partTypeId, Long deviceModelId, PartQuality quality);

	Optional<SparePart> findFirstByPartTypeIdAndDeviceModelIdAndQuality(Long partTypeId, Long deviceModelId,
			PartQuality quality);

	boolean existsByPartTypeIdAndDeviceModelIdAndQualityAndIdNot(Long partTypeId, Long deviceModelId,
			PartQuality quality, Long id);

	boolean existsByPartTypeId(Long partTypeId);

	@EntityGraph(attributePaths = { "partType", "deviceModel", "deviceModel.brand" })
	Optional<SparePart> findByIdAndActiveTrue(Long id);

	// Repuestos activos de un tipo y modelo opcionales (principal o compatible), para elegir el que se usa en una orden
	@EntityGraph(attributePaths = { "partType", "deviceModel", "deviceModel.brand" })
	@Query("""
			SELECT p FROM SparePart p
			WHERE p.active = true
			  AND (:partTypeId IS NULL OR p.partType.id = :partTypeId)
			  AND (:deviceModelId IS NULL OR p.deviceModel.id = :deviceModelId
			       OR EXISTS (SELECT 1 FROM SparePartCompatibility c
			                  WHERE c.sparePart = p AND c.deviceModel.id = :deviceModelId))
			ORDER BY p.name
			""")
	List<SparePart> findCandidates(@Param("partTypeId") Long partTypeId, @Param("deviceModelId") Long deviceModelId);

	// Filtros combinables del listado; el modelo incluye los compatibles y el texto ignora mayúsculas y tildes
	String SEARCH_WHERE = """
			WHERE p.active = true
			  AND (:partTypeId IS NULL OR p.partType.id = :partTypeId)
			  AND (:deviceModelId IS NULL OR p.deviceModel.id = :deviceModelId
			       OR EXISTS (SELECT 1 FROM SparePartCompatibility c
			                  WHERE c.sparePart = p AND c.deviceModel.id = :deviceModelId))
			  AND (:brandId IS NULL OR p.deviceModel.brand.id = :brandId)
			  AND (:quality IS NULL OR p.quality = :quality)
			  AND (:stockStatus IS NULL
			       OR (:stockStatus = 'OUT' AND p.stock <= 0)
			       OR (:stockStatus = 'LOW' AND p.stock > 0 AND p.stock <= p.minStock)
			       OR (:stockStatus = 'OK' AND p.stock > 0 AND p.stock > p.minStock))
			  AND (:alertsOnly = false OR p.stock <= p.minStock)
			  AND (:pattern IS NULL
			       OR collate(p.name as utf8mb4_0900_ai_ci) LIKE :pattern ESCAPE '!'
			       OR collate(p.description as utf8mb4_0900_ai_ci) LIKE :pattern ESCAPE '!'
			       OR collate(p.sku as utf8mb4_0900_ai_ci) LIKE :pattern ESCAPE '!')
			""";

	// Listado paginado con orden del servidor; pattern ya llega con los comodines escapados y entre %
	@EntityGraph(attributePaths = { "partType", "deviceModel", "deviceModel.brand" })
	@Query(value = "SELECT p FROM SparePart p " + SEARCH_WHERE,
			countQuery = "SELECT COUNT(p) FROM SparePart p " + SEARCH_WHERE)
	Page<SparePart> search(@Param("pattern") String pattern, @Param("partTypeId") Long partTypeId,
			@Param("deviceModelId") Long deviceModelId, @Param("brandId") Long brandId,
			@Param("quality") PartQuality quality, @Param("stockStatus") String stockStatus,
			@Param("alertsOnly") boolean alertsOnly, Pageable pageable);

	// Informe de inventario: existencias y valor a costo por repuesto, con los mismos filtros del listado
	@Query("""
			SELECT new com.fixtrack.inventory.dto.report.InventoryReportRow(p.id, p.sku, p.name, p.partType.name,
			  p.deviceModel.brand.name, p.deviceModel.name, p.quality, p.stock, p.minStock, p.costPrice,
			  p.stock * p.costPrice)
			FROM SparePart p
			""" + SEARCH_WHERE + " ORDER BY p.name, p.id")
	List<InventoryReportRow> inventoryReport(@Param("pattern") String pattern, @Param("partTypeId") Long partTypeId,
			@Param("deviceModelId") Long deviceModelId, @Param("brandId") Long brandId,
			@Param("quality") PartQuality quality, @Param("stockStatus") String stockStatus,
			@Param("alertsOnly") boolean alertsOnly);

	// Totales del informe de inventario sumados en la base de datos
	@Query("""
			SELECT new com.fixtrack.inventory.dto.report.InventoryTotals(COUNT(p), COALESCE(SUM(p.stock), 0),
			  COALESCE(SUM(p.stock * p.costPrice), 0),
			  COALESCE(SUM(CASE WHEN p.stock <= p.minStock THEN 1 ELSE 0 END), 0),
			  COALESCE(SUM(CASE WHEN p.stock <= 0 THEN 1 ELSE 0 END), 0))
			FROM SparePart p
			""" + SEARCH_WHERE)
	InventoryTotals inventoryTotals(@Param("pattern") String pattern, @Param("partTypeId") Long partTypeId,
			@Param("deviceModelId") Long deviceModelId, @Param("brandId") Long brandId,
			@Param("quality") PartQuality quality, @Param("stockStatus") String stockStatus,
			@Param("alertsOnly") boolean alertsOnly);

	// Alertas: repuestos activos en el mínimo o sin stock, primero los agotados
	@EntityGraph(attributePaths = { "partType", "deviceModel", "deviceModel.brand" })
	@Query("""
			SELECT p FROM SparePart p
			WHERE p.active = true AND p.stock <= p.minStock
			ORDER BY p.stock, p.name
			""")
	List<SparePart> findLowStock();
}
