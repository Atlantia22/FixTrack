package com.fixtrack.intervention.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.fixtrack.intervention.dto.InterventionFilter;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.mapper.InterventionCode;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public final class InterventionSpecifications {

	private static final Set<InterventionStatus> CAN_BE_OVERDUE = EnumSet.of(InterventionStatus.RECEIVED,
			InterventionStatus.IN_DIAGNOSIS, InterventionStatus.WAITING_PARTS, InterventionStatus.IN_REPAIR);
	private static final Set<InterventionStatus> IN_PROGRESS = EnumSet.of(InterventionStatus.IN_DIAGNOSIS,
			InterventionStatus.WAITING_PARTS, InterventionStatus.IN_REPAIR);
	private static final Set<InterventionStatus> CLOSED = EnumSet.of(InterventionStatus.DELIVERED,
			InterventionStatus.CANCELLED);

	private InterventionSpecifications() {
	}

	// Une los filtros que llegan con valor; el código ya viene convertido a id (y número de garantía)
	// y el documento como patrón LIKE escapado con '!'
	// unclaimedBefore: las listas desde antes de ese momento cuentan como no reclamadas
	public static Specification<Intervention> matching(InterventionFilter filter, InterventionCode.Parsed code,
			LocalDateTime now, LocalDateTime unclaimedBefore) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (code != null) {
				if (code.warrantyNumber() == null) {
					predicates.add(cb.equal(root.get("id"), code.baseId()));
				} else {
					predicates.add(cb.equal(root.get("warrantyOf").get("id"), code.baseId()));
					predicates.add(cb.equal(root.get("warrantyNumber"), code.warrantyNumber()));
				}
			}
			if (filter.documentNumber() != null) {
				predicates.add(cb.like(root.get("device").get("owner").get("documentNumber"), filter.documentNumber(), '!'));
			}
			if (filter.customerId() != null) {
				predicates.add(cb.equal(root.get("device").get("owner").get("id"), filter.customerId()));
			}
			if (filter.technicianId() != null) {
				predicates.add(cb.equal(root.get("technician").get("id"), filter.technicianId()));
			}
			if (filter.status() != null) {
				predicates.add(cb.equal(root.get("status"), filter.status()));
			} else if (filter.openOnly()) {
				predicates.add(cb.not(root.get("status").in(CLOSED)));
			}
			if (filter.receivedFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("receivedAt"), filter.receivedFrom().atStartOfDay()));
			}
			if (filter.receivedTo() != null) {
				predicates.add(cb.lessThan(root.get("receivedAt"), filter.receivedTo().plusDays(1).atStartOfDay()));
			}
			if (filter.inProgress()) {
				predicates.add(root.get("status").in(IN_PROGRESS));
			}
			if (filter.unclaimed()) {
				predicates.add(cb.equal(root.get("status"), InterventionStatus.READY));
				predicates.add(cb.lessThan(root.get("readyAt"), unclaimedBefore));
			}
			if (filter.overdue() != null) {
				Predicate overdue = overdue(root, cb, now);
				predicates.add(filter.overdue() ? overdue : cb.not(overdue));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	// Entregadas dentro de [from, to), para la lista del día en la Salida
	public static Specification<Intervention> deliveredBetween(LocalDateTime from, LocalDateTime to) {
		return (root, query, cb) -> cb.and(cb.equal(root.get("status"), InterventionStatus.DELIVERED),
				cb.greaterThanOrEqualTo(root.get("deliveredAt"), from), cb.lessThan(root.get("deliveredAt"), to));
	}

	// Atrasada (misma regla de DeliveryDeadline): no está lista, entregada ni cancelada y se cumplió el plazo;
	// con hora, desde ese minuto; sin hora (órdenes antiguas), desde el día siguiente a la fecha
	private static Predicate overdue(Root<Intervention> root, CriteriaBuilder cb, LocalDateTime now) {
		Predicate dayPassed = cb.lessThan(root.get("estimatedDeliveryDate"), now.toLocalDate());
		Predicate timePassed = cb.and(cb.equal(root.get("estimatedDeliveryDate"), now.toLocalDate()),
				cb.isNotNull(root.get("estimatedDeliveryTime")),
				cb.lessThanOrEqualTo(root.get("estimatedDeliveryTime"), now.toLocalTime()));
		return cb.and(cb.or(dayPassed, timePassed), root.get("status").in(CAN_BE_OVERDUE));
	}
}
