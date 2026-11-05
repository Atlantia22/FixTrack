package com.fixtrack.intervention.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fixtrack.intervention.entity.ServiceType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Garantía sobre una orden entregada; el celular y el correo del cliente se pueden actualizar con la regla de «No tiene»
// (si no llegan ni el dato ni la marca, se conserva el que tenía)
public record WarrantyRequest(
		@NotNull ServiceType serviceType,
		@NotBlank @Size(max = 1000) String problemDescription,
		@NotBlank @Size(max = 1000) String physicalCondition,
		@Size(max = 100) String unlockCode,
		@NotNull LocalDate estimatedDeliveryDate,
		@NotNull LocalTime estimatedDeliveryTime,
		Long technicianId,
		@Pattern(regexp = "^(\\s*\\d{7,10}\\s*)?$", message = "debe tener entre 7 y 10 dígitos, solo números") String phone,
		Boolean noPhone,
		@Email @Size(max = 150) String email,
		Boolean noEmail) {
}
