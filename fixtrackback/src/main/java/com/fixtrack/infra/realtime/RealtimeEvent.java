package com.fixtrack.infra.realtime;

import java.time.Instant;
import java.util.Map;

// Mensaje que recibe el front por WebSocket: qué pasó, a qué registro y unos pocos datos no sensibles para pintarlo
public record RealtimeEvent(
		RealtimeEventType type,
		String entity,
		Long id,
		Map<String, Object> payload,
		Instant timestamp) {
}
