package com.fixtrack.infra.realtime;

import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Envía los eventos al broker solo después del commit: si la transacción se revierte, nadie recibe el aviso
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeBroadcaster {

	private final SimpMessagingTemplate messagingTemplate;

	// fallbackExecution: si algo publica fuera de una transacción, el evento sale de inmediato
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void broadcast(RealtimeMessage message) {
		try {
			messagingTemplate.convertAndSend(message.destination(), message.event());
		} catch (MessagingException ex) {
			// El cambio ya está guardado; un aviso perdido no debe convertirse en un error para quien lo hizo
			log.warn("No se pudo enviar el evento {} a {}", message.event().type(), message.destination(), ex);
		}
	}
}
