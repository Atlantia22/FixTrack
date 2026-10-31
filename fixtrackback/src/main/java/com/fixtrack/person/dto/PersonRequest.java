package com.fixtrack.person.dto;

import java.util.List;

import com.fixtrack.person.entity.DocumentType;
import com.fixtrack.person.entity.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Alta y edición de personas; noPhone y noEmail marcan explícitamente que el cliente no tiene ese dato
public record PersonRequest(
		@NotNull DocumentType documentType,
		@NotBlank @Size(max = 15) @Pattern(regexp = "^\\s*[A-Za-z0-9]+\\s*$", message = "solo puede tener letras y números") String documentNumber,
		@NotBlank @Size(max = 150) String fullName,
		@Pattern(regexp = "^(\\s*\\d{7,10}\\s*)?$", message = "debe tener entre 7 y 10 dígitos, solo números") String phone,
		Boolean noPhone,
		@Email @Size(max = 150) String email,
		Boolean noEmail,
		@Size(min = 8, max = 72) String password,
		@NotNull Role role,
		List<Long> specialtyIds) {
}
