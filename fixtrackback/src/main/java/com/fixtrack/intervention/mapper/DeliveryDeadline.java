package com.fixtrack.intervention.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.fixtrack.intervention.entity.InterventionStatus;

// Momento de entrega acordado (fecha + hora en Bogotá) y la regla de atraso; el tiempo restante no se guarda, se calcula
public final class DeliveryDeadline {

	public static final ZoneId ZONE = ZoneId.of("America/Bogota");

	private DeliveryDeadline() {
	}

	// Fecha y hora con zona (ISO-8601) para el front; null en las órdenes antiguas sin hora
	public static OffsetDateTime at(LocalDate date, LocalTime time) {
		return date == null || time == null ? null : date.atTime(time).atZone(ZONE).toOffsetDateTime();
	}

	// Atrasada si no está lista, entregada ni cancelada y ya se cumplió el plazo: con hora, desde ese minuto;
	// sin hora (órdenes antiguas), desde el día siguiente a la fecha, como antes
	public static boolean isOverdue(InterventionStatus status, LocalDate date, LocalTime time, LocalDateTime now) {
		if (!status.canBeOverdue() || date == null) {
			return false;
		}
		return time == null ? date.isBefore(now.toLocalDate()) : !date.atTime(time).isAfter(now);
	}
}
