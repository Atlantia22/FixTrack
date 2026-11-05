package com.fixtrack.intervention.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RescheduleDeliveryRequest(@NotNull LocalDate newDate, @NotNull LocalTime newTime,
		@NotBlank @Size(max = 500) String reason) {
}
