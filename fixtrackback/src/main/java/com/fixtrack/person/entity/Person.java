package com.fixtrack.person.entity;

import java.time.Instant;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Clientes y usuarios internos; solo los internos tienen correo obligatorio y contraseña
@Entity
@Table(name = "person", uniqueConstraints = {
		@UniqueConstraint(name = "uk_person_document", columnNames = { "document_type", "document_number" }),
		@UniqueConstraint(name = "uk_person_email", columnNames = "email") })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Person {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false, length = 20)
	private DocumentType documentType;

	@Column(name = "document_number", nullable = false, length = 20)
	private String documentNumber;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	// null cuando se marca «No tiene»; se muestra como NT
	@Column(name = "phone", length = 10)
	private String phone;

	// null cuando se marca «No tiene», para que la restricción única lo ignore
	@Column(name = "email", length = 150)
	private String email;

	// Hash bcrypt, solo para usuarios internos
	@Column(name = "password", length = 100)
	private String password;

	// Último cambio de contraseña, al segundo: los tokens emitidos antes dejan de servir
	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private Role role;

	// Solo para técnicos
	@Enumerated(EnumType.STRING)
	@Column(name = "availability", length = 20)
	private Availability availability;

	@Builder.Default
	@Column(name = "active", nullable = false)
	private boolean active = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
