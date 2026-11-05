package com.fixtrack.intervention.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fixtrack.intervention.dto.OrderCounters;
import com.fixtrack.intervention.dto.report.OrderProfitRow;
import com.fixtrack.intervention.dto.report.StatusCount;
import com.fixtrack.intervention.dto.report.TechnicianMaintenance;
import com.fixtrack.intervention.dto.report.TechnicianRevenue;
import com.fixtrack.intervention.dto.TechnicianWorkload;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionStatus;

import jakarta.persistence.LockModeType;

public interface InterventionRepository extends JpaRepository<Intervention, Long>,
		JpaSpecificationExecutor<Intervention> {

	// Listado con cliente, equipo y técnico en la misma consulta
	@Override
	@EntityGraph(attributePaths = { "device", "device.owner", "device.deviceModel", "device.deviceModel.brand",
			"technician", "warrantyOf" })
	Page<Intervention> findAll(Specification<Intervention> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = { "device", "device.owner", "device.deviceModel", "device.deviceModel.brand",
			"technician", "warrantyOf" })
	List<Intervention> findAll(Specification<Intervention> spec, Sort sort);

	@EntityGraph(attributePaths = { "device", "device.owner", "device.deviceModel", "device.deviceModel.brand",
			"technician", "advisor", "warrantyOf" })
	Optional<Intervention> findDetailById(Long id);

	// Bloquea la orden original para numerar sus garantías sin que dos asesores tomen el mismo número
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT i FROM Intervention i WHERE i.id = :id")
	Optional<Intervention> findForUpdate(@Param("id") Long id);

	@Query("SELECT COALESCE(MAX(i.warrantyNumber), 0) FROM Intervention i WHERE i.warrantyOf.id = :originalId")
	int findMaxWarrantyNumber(@Param("originalId") Long originalId);

	List<Intervention> findByWarrantyOfIdOrderByWarrantyNumberAsc(Long originalId);

	// Informe de mantenimientos: órdenes recibidas en [from, to) por estado
	@Query("""
			SELECT new com.fixtrack.intervention.dto.report.StatusCount(i.status, COUNT(i))
			FROM Intervention i
			WHERE i.receivedAt >= :from AND i.receivedAt < :to
			GROUP BY i.status
			""")
	List<StatusCount> countByStatusReceivedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// Informe de mantenimientos: órdenes recibidas en [from, to) por técnico (null = sin asignar)
	@Query("""
			SELECT new com.fixtrack.intervention.dto.report.TechnicianMaintenance(t.id, t.fullName, COUNT(i),
			  SUM(CASE WHEN i.status IN :inProgressStatuses THEN 1 ELSE 0 END),
			  SUM(CASE WHEN i.status = :ready THEN 1 ELSE 0 END),
			  SUM(CASE WHEN i.status = :delivered THEN 1 ELSE 0 END),
			  SUM(CASE WHEN i.status = :cancelled THEN 1 ELSE 0 END),
			  SUM(CASE WHEN i.warrantyOf IS NOT NULL THEN 1 ELSE 0 END))
			FROM Intervention i LEFT JOIN i.technician t
			WHERE i.receivedAt >= :from AND i.receivedAt < :to
			GROUP BY t.id, t.fullName
			ORDER BY t.fullName
			""")
	List<TechnicianMaintenance> countByTechnicianReceivedBetween(@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			@Param("inProgressStatuses") Collection<InterventionStatus> inProgressStatuses,
			@Param("ready") InterventionStatus ready, @Param("delivered") InterventionStatus delivered,
			@Param("cancelled") InterventionStatus cancelled);

	// Informe de ganancias: cada orden entregada en [from, to) con lo cobrado y su costo de repuestos
	@Query("""
			SELECT new com.fixtrack.intervention.dto.report.OrderProfitRow(i.id, o.id, i.warrantyNumber, i.deliveredAt,
			  c.fullName, t.id, t.fullName, COALESCE(i.totalPrice, 0),
			  COALESCE((SELECT SUM(u.quantity * u.costPrice) FROM InterventionSparePart u WHERE u.intervention = i), 0))
			FROM Intervention i JOIN i.device d JOIN d.owner c LEFT JOIN i.technician t LEFT JOIN i.warrantyOf o
			WHERE i.status = :delivered AND i.deliveredAt >= :from AND i.deliveredAt < :to
			  AND (:technicianId IS NULL OR t.id = :technicianId)
			ORDER BY i.deliveredAt, i.id
			""")
	List<OrderProfitRow> findProfitRows(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("technicianId") Long technicianId, @Param("delivered") InterventionStatus delivered);

	// Informe de ganancias: lo cobrado por técnico en las órdenes entregadas en [from, to)
	@Query("""
			SELECT new com.fixtrack.intervention.dto.report.TechnicianRevenue(t.id, t.fullName, COUNT(i),
			  COALESCE(SUM(i.totalPrice), 0), SUM(CASE WHEN i.warrantyOf IS NOT NULL THEN 1 ELSE 0 END))
			FROM Intervention i LEFT JOIN i.technician t
			WHERE i.status = :delivered AND i.deliveredAt >= :from AND i.deliveredAt < :to
			  AND (:technicianId IS NULL OR t.id = :technicianId)
			GROUP BY t.id, t.fullName
			ORDER BY t.fullName
			""")
	List<TechnicianRevenue> sumRevenueByTechnician(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("technicianId") Long technicianId, @Param("delivered") InterventionStatus delivered);

	// Contadores del panel de Inicio en una sola consulta; technicianId null cuenta las órdenes de todo el taller
	@Query("""
			SELECT new com.fixtrack.intervention.dto.OrderCounters(
			  COALESCE(SUM(CASE WHEN i.receivedAt >= :startOfToday AND i.receivedAt < :startOfTomorrow THEN 1 ELSE 0 END), 0),
			  COALESCE(SUM(CASE WHEN i.status IN :overdueStatuses AND (i.estimatedDeliveryDate < :today
			    OR (i.estimatedDeliveryDate = :today AND i.estimatedDeliveryTime IS NOT NULL
			        AND i.estimatedDeliveryTime <= :nowTime)) THEN 1 ELSE 0 END), 0),
			  COALESCE(SUM(CASE WHEN i.status IN :inProgressStatuses THEN 1 ELSE 0 END), 0),
			  COALESCE(SUM(CASE WHEN i.status = :waitingParts THEN 1 ELSE 0 END), 0),
			  COALESCE(SUM(CASE WHEN i.status = :ready THEN 1 ELSE 0 END), 0),
			  COALESCE(SUM(CASE WHEN i.status = :ready AND i.readyAt < :unclaimedBefore THEN 1 ELSE 0 END), 0),
			  COALESCE(SUM(CASE WHEN i.status NOT IN :closedStatuses THEN 1 ELSE 0 END), 0))
			FROM Intervention i
			WHERE (:technicianId IS NULL OR i.technician.id = :technicianId)
			""")
	OrderCounters countForDashboard(@Param("technicianId") Long technicianId,
			@Param("startOfToday") LocalDateTime startOfToday, @Param("startOfTomorrow") LocalDateTime startOfTomorrow,
			@Param("today") LocalDate today, @Param("nowTime") LocalTime nowTime,
			@Param("unclaimedBefore") LocalDateTime unclaimedBefore,
			@Param("overdueStatuses") Collection<InterventionStatus> overdueStatuses,
			@Param("inProgressStatuses") Collection<InterventionStatus> inProgressStatuses,
			@Param("closedStatuses") Collection<InterventionStatus> closedStatuses,
			@Param("waitingParts") InterventionStatus waitingParts, @Param("ready") InterventionStatus ready);

	// Órdenes abiertas de cada técnico, para mostrar su carga junto a su disponibilidad
	@Query("""
			SELECT new com.fixtrack.intervention.dto.TechnicianWorkload(i.technician.id, COUNT(i))
			FROM Intervention i
			WHERE i.technician IS NOT NULL AND i.status NOT IN :closedStatuses
			GROUP BY i.technician.id
			""")
	List<TechnicianWorkload> countOpenByTechnician(@Param("closedStatuses") Collection<InterventionStatus> closedStatuses);
}
