package com.fixtrack.intervention.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fixtrack.device.dto.DeviceRequest;
import com.fixtrack.intervention.entity.ServiceType;
import com.fixtrack.person.dto.CustomerRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInterventionRequest(
		@NotNull @Valid CustomerRequest customer,
		@NotNull @Valid DeviceRequest device,
		@NotEmpty(message = "Elija al menos un daño") @Size(max = 10) List<@NotNull ServiceType> serviceTypes,
		@NotBlank @Size(max = 1000) String problemDescription,
		@NotBlank @Size(max = 1000) String physicalCondition,
		@Size(max = 100) String unlockCode,
		@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal estimatedPrice,
		@NotNull LocalDate estimatedDeliveryDate,
		@NotNull LocalTime estimatedDeliveryTime,
		Long technicianId) {
}
