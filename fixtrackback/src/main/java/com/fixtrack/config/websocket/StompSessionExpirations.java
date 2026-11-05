package com.fixtrack.config.websocket;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Sesiones STOMP autenticadas: de quién son y hasta cuándo vale su token. La conexión vive más que el token y hay que
// cortarla a tiempo, también cuando el usuario se da de baja o cambia de rol o de contraseña antes de que venza
@Component
public class StompSessionExpirations {

	private record SessionAccess(Long userId, Instant expiresAt) {
	}

	private final Map<String, SessionAccess> sessions = new ConcurrentHashMap<>();

	public void register(String sessionId, Long userId, Instant expiresAt) {
		if (sessionId != null && expiresAt != null) {
			sessions.put(sessionId, new SessionAccess(userId, expiresAt));
		}
	}

	public void remove(String sessionId) {
		if (sessionId != null) {
			sessions.remove(sessionId);
		}
	}

	// Revoca de inmediato todas las sesiones del usuario: dejan de recibir mensajes y no pueden suscribirse
	public void expireUser(Long userId, Instant now) {
		sessions.replaceAll((sessionId, access) -> userId.equals(access.userId())
				? new SessionAccess(access.userId(), now)
				: access);
	}

	// Las sesiones anónimas del portal no se registran y nunca vencen por token
	public boolean isExpired(String sessionId, Instant now) {
		SessionAccess access = sessionId == null ? null : sessions.get(sessionId);
		return access != null && !access.expiresAt().isAfter(now);
	}
}
