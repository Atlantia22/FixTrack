package com.fixtrack.intervention.dto.portal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.fixtrack.device.entity.DeviceType;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.entity.RepairOutcome;

// Lo que ve el cliente: sin costos internos, sin datos del técnico y sin la contraseña de desbloqueo
public record PortalOrderResponse(
		String code,
		DeviceType deviceType,
		String brandName,
		String modelName,
		InterventionStatus status,
		RepairOutcome repairOutcome,
		LocalDateTime receivedAt,
		LocalDate estimatedDeliveryDate,
		LocalTime estimatedDeliveryTime,
		LocalDateTime deliveredAt,
		BigDecimal estimatedPrice,
		BigDecimal total,
		Integer warrantyDays,
		LocalDate warrantyUntil,
		boolean warrantyActive,
		List<PortalTimelineEntry> timeline,
		List<PortalWarrantyResponse> warranties) {
}
