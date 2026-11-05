package com.fixtrack.config.websocket;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Vencimiento del token de cada sesión STOMP autenticada; la conexión vive más que el token y hay que cortarla a tiempo
@Component
public class StompSessionExpirations {

	private final Map<String, Instant> expirations = new ConcurrentHashMap<>();

	public void register(String sessionId, Instant expiresAt) {
		if (sessionId != null && expiresAt != null) {
			expirations.put(sessionId, expiresAt);
		}
	}

	public void remove(String sessionId) {
		if (sessionId != null) {
			expirations.remove(sessionId);
		}
	}

	// Las sesiones anónimas del portal no se registran y nunca vencen por token
	public boolean isExpired(String sessionId, Instant now) {
		Instant expiresAt = sessionId == null ? null : expirations.get(sessionId);
		return expiresAt != null && !expiresAt.isAfter(now);
	}
}
