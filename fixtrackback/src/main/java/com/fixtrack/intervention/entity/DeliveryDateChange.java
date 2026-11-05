package com.fixtrack.intervention.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;

import com.fixtrack.person.entity.Person;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Registro de cada reprogramación de la fecha estimada de entrega
@Entity
@Table(name = "delivery_date_change")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DeliveryDateChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "intervention_id", nullable = false)
	private Intervention intervention;

	@Column(name = "previous_date", nullable = false)
	private LocalDate previousDate;

	@Column(name = "new_date", nullable = false)
	private LocalDate newDate;

	// Horas de la reprogramación; null cuando la orden no tenía hora (órdenes antiguas)
	@Column(name = "previous_time")
	private LocalTime previousTime;

	@Column(name = "new_time")
	private LocalTime newTime;

	@Column(name = "reason", nullable = false, length = 500)
	private String reason;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "changed_by_id", nullable = false)
	private Person changedBy;

	@CreationTimestamp
	@Column(name = "changed_at", nullable = false, updatable = false)
	private LocalDateTime changedAt;
}
