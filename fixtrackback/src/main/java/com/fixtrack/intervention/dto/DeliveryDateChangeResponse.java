package com.fixtrack.intervention.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record DeliveryDateChangeResponse(Long id, LocalDate previousDate, LocalTime previousTime, LocalDate newDate,
		LocalTime newTime, String reason, PersonRef changedBy, LocalDateTime changedAt) {
}
