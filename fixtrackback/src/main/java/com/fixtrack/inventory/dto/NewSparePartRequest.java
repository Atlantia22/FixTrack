package com.fixtrack.inventory.dto;

import java.math.BigDecimal;

import com.fixtrack.inventory.entity.PartQuality;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NewSparePartRequest(
		@NotNull Long partTypeId,
		@NotBlank @Size(max = 150) String name,
		PartQuality quality,
		@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
		@NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal salePrice) {
}
