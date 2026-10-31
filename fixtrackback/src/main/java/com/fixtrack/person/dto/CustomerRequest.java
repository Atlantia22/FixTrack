package com.fixtrack.person.dto;

import com.fixtrack.person.entity.DocumentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Cliente en el ingreso de una orden: se busca por documento; si existe, solo se actualizan celular y correo
public record CustomerRequest(
		@NotNull DocumentType documentType,
		@NotBlank @Size(max = 15) @Pattern(regexp = "^\\s*[A-Za-z0-9]+\\s*$", message = "solo puede tener letras y números") String documentNumber,
		@Size(max = 150) String fullName,
		@Pattern(regexp = "^(\\s*\\d{7,10}\\s*)?$", message = "debe tener entre 7 y 10 dígitos, solo números") String phone,
		Boolean noPhone,
		@Email @Size(max = 150) String email,
		Boolean noEmail) {
}
