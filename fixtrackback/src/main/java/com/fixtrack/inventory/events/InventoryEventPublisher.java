package com.fixtrack.inventory.events;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fixtrack.infra.realtime.RealtimeEventType;
import com.fixtrack.infra.realtime.RealtimePublisher;
import com.fixtrack.infra.realtime.RealtimeTopics;
import com.fixtrack.inventory.entity.PartRequest;
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.entity.StockStatus;

import lombok.RequiredArgsConstructor;

// Avisos en tiempo real del inventario: cambios de repuestos, de stock (con su alerta) y de solicitudes de repuestos
@Component
@RequiredArgsConstructor
public class InventoryEventPublisher {

	private static final String SPARE_PART = "spare-part";
	private static final String PART_REQUEST = "part-request";

	private final RealtimePublisher publisher;

	public void sparePartChanged(RealtimeEventType type, SparePart part) {
		publisher.publish(RealtimeTopics.SPARE_PARTS, type, SPARE_PART, part.getId(), stockPayload(part));
	}

	// Cada movimiento avisa el stock nuevo; si cruza el mínimo o se agota, además sale la alerta de stock
	public void stockChanged(SparePart part, int previousStock) {
		publisher.publish(RealtimeTopics.SPARE_PARTS, RealtimeEventType.STOCK_CHANGED, SPARE_PART, part.getId(),
				stockPayload(part));

		StockStatus before = StockStatus.of(previousStock, part.getMinStock());
		StockStatus after = StockStatus.of(part.getStock(), part.getMinStock());
		RealtimeEventType alert = alertFor(before, after);
		if (alert != null) {
			Map<String, Object> payload = stockPayload(part);
			payload.put("name", part.getName());
			publisher.publish(RealtimeTopics.STOCK_ALERTS, alert, SPARE_PART, part.getId(), payload);
		}
	}

	public void partRequestChanged(RealtimeEventType type, PartRequest request) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("status", request.getStatus());
		payload.put("interventionId", request.getIntervention().getId());
		payload.put("requestedById", request.getRequestedBy() == null ? null : request.getRequestedBy().getId());
		publisher.publish(RealtimeTopics.PART_REQUESTS, type, PART_REQUEST, request.getId(), payload);
		// El técnico solo recibe sus solicitudes, en su cola privada
		if (request.getRequestedBy() != null) {
			publisher.publishToUser(request.getRequestedBy().getEmail(), RealtimeTopics.MY_PART_REQUESTS, type,
					PART_REQUEST, request.getId(), payload);
		}
	}

	// Solo los cambios de estado de stock generan alerta: bajar al mínimo, agotarse o volver a tener suficiente
	static RealtimeEventType alertFor(StockStatus before, StockStatus after) {
		if (before == after) {
			return null;
		}
		return switch (after) {
			case OUT -> RealtimeEventType.OUT_OF_STOCK;
			case LOW -> before == StockStatus.OK ? RealtimeEventType.LOW_STOCK : null;
			case OK -> RealtimeEventType.STOCK_RESTORED;
		};
	}

	private Map<String, Object> stockPayload(SparePart part) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sku", part.getSku());
		payload.put("stock", part.getStock());
		payload.put("minStock", part.getMinStock());
		payload.put("stockStatus", StockStatus.of(part.getStock(), part.getMinStock()));
		return payload;
	}
}
