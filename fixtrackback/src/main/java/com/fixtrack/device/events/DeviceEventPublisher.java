package com.fixtrack.device.events;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fixtrack.device.entity.Device;
import com.fixtrack.infra.realtime.RealtimeEventType;
import com.fixtrack.infra.realtime.RealtimePublisher;
import com.fixtrack.infra.realtime.RealtimeTopics;

import lombok.RequiredArgsConstructor;

// Avisos en tiempo real de los equipos de los clientes
@Component
@RequiredArgsConstructor
public class DeviceEventPublisher {

	private final RealtimePublisher publisher;

	public void deviceChanged(RealtimeEventType type, Device device) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("ownerId", device.getOwner().getId());
		publisher.publish(RealtimeTopics.DEVICES, type, "device", device.getId(), payload);
	}
}
