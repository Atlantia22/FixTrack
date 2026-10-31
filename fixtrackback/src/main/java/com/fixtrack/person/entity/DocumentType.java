package com.fixtrack.person.entity;

import java.util.regex.Pattern;

public enum DocumentType {
	CC("^[0-9]{6,10}$", "La cédula de ciudadanía debe tener de 6 a 10 números"),
	TI("^[0-9]{10,11}$", "La tarjeta de identidad debe tener 10 u 11 números"),
	CE("^[0-9]{6,12}$", "La cédula de extranjería debe tener de 6 a 12 números"),
	PASSPORT("^[A-Z0-9]{6,15}$", "El pasaporte debe tener de 6 a 15 letras o números"),
	PPT("^[0-9]{6,12}$", "El PPT debe tener de 6 a 12 números");

	private final Pattern numberPattern;
	private final String rule;

	DocumentType(String numberPattern, String rule) {
		this.numberPattern = Pattern.compile(numberPattern);
		this.rule = rule;
	}

	// Formato del número según el tipo: solo números (salvo el pasaporte) y su largo permitido
	public boolean isValidNumber(String documentNumber) {
		return documentNumber != null && numberPattern.matcher(documentNumber).matches();
	}

	public String rule() {
		return rule;
	}
}
