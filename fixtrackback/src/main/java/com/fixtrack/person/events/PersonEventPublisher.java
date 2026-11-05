package com.fixtrack.person.events;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.fixtrack.infra.realtime.RealtimeEventType;
import com.fixtrack.infra.realtime.RealtimePublisher;
import com.fixtrack.infra.realtime.RealtimeTopics;
import com.fixtrack.person.entity.Person;

import lombok.RequiredArgsConstructor;

// Avisos en tiempo real de personas (clientes y personal); sin celular, correo ni documento
@Component
@RequiredArgsConstructor
public class PersonEventPublisher {

	private final RealtimePublisher publisher;
	private final ApplicationEventPublisher applicationEventPublisher;

	public void personChanged(RealtimeEventType type, Person person) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("role", person.getRole());
		payload.put("active", person.isActive());
		payload.put("availability", person.getAvailability());
		publisher.publish(RealtimeTopics.PERSONS, type, "person", person.getId(), payload);
	}

	// Baja, cambio de rol o de contraseña: la sesión en tiempo real abierta de esa persona deja de servir al confirmar
	public void accessRevoked(Person person) {
		applicationEventPublisher.publishEvent(new PersonAccessRevokedEvent(person.getId()));
	}
}
