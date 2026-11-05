package com.fixtrack.infra.realtime;

import java.time.Clock;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// Punto único que usan los publicadores de cada dominio: arma el evento y lo deja en la transacción en curso
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

	private final ApplicationEventPublisher applicationEventPublisher;
	private final Clock clock;

	public void publish(String destination, RealtimeEventType type, String entity, Long id,
			Map<String, Object> payload) {
		RealtimeEvent event = new RealtimeEvent(type, entity, id, payload, clock.instant());
		applicationEventPublisher.publishEvent(new RealtimeMessage(destination, event));
	}
}
