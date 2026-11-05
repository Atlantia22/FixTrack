package com.fixtrack.config.websocket;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fixtrack.person.events.PersonAccessRevokedEvent;

import lombok.RequiredArgsConstructor;

// Corta los avisos en tiempo real de una persona en cuanto pierde el acceso, sin esperar a que venza su token
@Component
@RequiredArgsConstructor
public class RealtimeAccessRevoker {

	private final StompSessionExpirations expirations;
	private final Clock clock;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void revoke(PersonAccessRevokedEvent event) {
		expirations.expireUser(event.personId(), clock.instant());
	}
}
