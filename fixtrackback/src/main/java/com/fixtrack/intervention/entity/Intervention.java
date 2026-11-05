package com.fixtrack.intervention.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fixtrack.device.entity.Device;
import com.fixtrack.infra.security.EncryptedStringConverter;
import com.fixtrack.person.entity.Person;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Orden de reparación o garantía; su código visible (FT-00001, FT-00001-G1) se genera a partir del id
@Entity
@Table(name = "intervention", uniqueConstraints = @UniqueConstraint(name = "uk_intervention_warranty", columnNames = {
		"warranty_of_id", "warranty_number" }))
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Intervention {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Consecutivo de la garantía dentro de la orden original; null en órdenes normales
	@Column(name = "warranty_number")
	private Integer warrantyNumber;

	// Hash de la clave de consulta del portal; null en garantías, que usan la clave de la original
	@Column(name = "access_key_hash", length = 100)
	private String accessKeyHash;

	@Builder.Default
	@Column(name = "failed_lookup_attempts", nullable = false)
	private int failedLookupAttempts = 0;

	@Column(name = "lookup_locked_until")
	private LocalDateTime lookupLockedUntil;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "device_id", nullable = false)
	private Device device;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "advisor_id", nullable = false)
	private Person advisor;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "technician_id")
	private Person technician;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private InterventionStatus status = InterventionStatus.RECEIVED;

	@Column(name = "problem_description", nullable = false, length = 1000)
	private String problemDescription;

	@Column(name = "physical_condition", nullable = false, length = 1000)
	private String physicalCondition;

	// Contraseña o patrón de desbloqueo cifrado; se borra al entregar o cancelar
	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "unlock_code", length = 255)
	private String unlockCode;

	@Column(name = "diagnosis", length = 2000)
	private String diagnosis;

	@Column(name = "solution", length = 2000)
	private String solution;

	@Enumerated(EnumType.STRING)
	@Column(name = "repair_outcome", length = 30)
	private RepairOutcome repairOutcome;

	@Column(name = "outcome_reason", length = 500)
	private String outcomeReason;

	// Presupuesto inicial
	@Column(name = "estimated_price", precision = 12, scale = 2)
	private BigDecimal estimatedPrice;

	@Column(name = "estimated_delivery_date", nullable = false)
	private LocalDate estimatedDeliveryDate;

	// Hora acordada con el cliente; null en las órdenes anteriores a este campo, que solo tenían fecha
	@Column(name = "estimated_delivery_time")
	private LocalTime estimatedDeliveryTime;

	@Builder.Default
	@Column(name = "labor_cost", nullable = false, precision = 12, scale = 2)
	private BigDecimal laborCost = new BigDecimal("0.00");

	// Se guarda al entregar
	@Column(name = "total_price", precision = 12, scale = 2)
	private BigDecimal totalPrice;

	@Column(name = "warranty_days")
	private Integer warrantyDays;

	@Column(name = "warranty_until")
	private LocalDate warrantyUntil;

	// Orden original cuando esta es una garantía; nunca apunta a otra garantía
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "warranty_of_id")
	private Intervention warrantyOf;

	@Column(name = "delivery_notes", length = 1000)
	private String deliveryNotes;

	@CreationTimestamp
	@Column(name = "received_at", nullable = false, updatable = false)
	private LocalDateTime receivedAt;

	@Column(name = "ready_at")
	private LocalDateTime readyAt;

	@Column(name = "delivered_at")
	private LocalDateTime deliveredAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@Version
	@Column(name = "version")
	private Long version;
}
