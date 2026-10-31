package com.fixtrack.intervention.dto;

import com.fixtrack.inventory.dto.NewSparePartRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record NewPartUsageRequest(
		@NotNull @Valid NewSparePartRequest part,
		@NotNull @Positive @Max(1000) Integer quantityObtained,
		@NotNull @Positive @Max(1000) Integer quantityUsed) {
}
