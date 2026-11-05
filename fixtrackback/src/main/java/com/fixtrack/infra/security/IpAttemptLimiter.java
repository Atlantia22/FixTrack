package com.fixtrack.infra.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// Intentos fallidos por llave (IP del portal, IP del login, usuario que cambia su contraseña) en una ventana de 15 minutos;
// al llegar al máximo, esa llave espera.
// Vive en memoria: se reinicia con la aplicación y no se comparte entre instancias
@Component
@RequiredArgsConstructor
public class IpAttemptLimiter {

	private static final int MAX_FAILURES = 10;
	private static final Duration WINDOW = Duration.ofMinutes(15);
	private static final int MAX_TRACKED_IPS = 10_000;

	private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
	private final Clock clock;

	public boolean isBlocked(String ip) {
		Deque<Instant> attempts = failures.get(ip);
		if (attempts == null) {
			return false;
		}
		synchronized (attempts) {
			prune(attempts);
			return attempts.size() >= MAX_FAILURES;
		}
	}

	public void recordFailure(String ip) {
		if (failures.size() > MAX_TRACKED_IPS) {
			failures.entrySet().removeIf(entry -> {
				synchronized (entry.getValue()) {
					prune(entry.getValue());
					return entry.getValue().isEmpty();
				}
			});
		}
		Deque<Instant> attempts = failures.computeIfAbsent(ip, key -> new ArrayDeque<>());
		synchronized (attempts) {
			prune(attempts);
			attempts.addLast(clock.instant());
		}
	}

	private void prune(Deque<Instant> attempts) {
		Instant limit = clock.instant().minus(WINDOW);
		while (!attempts.isEmpty() && attempts.peekFirst().isBefore(limit)) {
			attempts.pollFirst();
		}
	}
}
