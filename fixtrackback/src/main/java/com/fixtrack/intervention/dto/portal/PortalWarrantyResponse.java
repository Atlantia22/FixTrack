package com.fixtrack.intervention.dto.portal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.entity.RepairOutcome;

public record PortalWarrantyResponse(
		String code,
		InterventionStatus status,
		RepairOutcome repairOutcome,
		LocalDateTime receivedAt,
		LocalDate estimatedDeliveryDate,
		LocalTime estimatedDeliveryTime,
		LocalDateTime deliveredAt,
		List<PortalTimelineEntry> timeline) {
}
