package com.fixtrack.intervention.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.entity.RepairOutcome;

// Detalle de la orden; unlockCode solo llega al asesor y al técnico asignado, laborCost no llega al asesor
// (sin desglose) y nextStatuses son las transiciones válidas
public record InterventionDetailResponse(
		Long id,
		String code,
		InterventionStatus status,
		boolean overdue,
		CustomerSummary customer,
		DeviceSummary device,
		PersonRef advisor,
		PersonRef technician,
		String problemDescription,
		String physicalCondition,
		String unlockCode,
		List<FindingResponse> findings,
		String diagnosis,
		String solution,
		RepairOutcome repairOutcome,
		String outcomeReason,
		BigDecimal estimatedPrice,
		BigDecimal laborCost,
		BigDecimal totalPrice,
		ChargesSummary charges,
		LocalDate estimatedDeliveryDate,
		LocalTime estimatedDeliveryTime,
		OffsetDateTime estimatedDeliveryAt,
		String warrantyOfCode,
		Integer warrantyDays,
		LocalDate warrantyUntil,
		List<WarrantySummary> warranties,
		String deliveryNotes,
		LocalDateTime receivedAt,
		LocalDateTime readyAt,
		LocalDateTime deliveredAt,
		List<InterventionStatus> nextStatuses) {
}
