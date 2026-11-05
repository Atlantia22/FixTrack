package com.fixtrack.intervention.events;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fixtrack.infra.realtime.RealtimeEventType;
import com.fixtrack.infra.realtime.RealtimePublisher;
import com.fixtrack.infra.realtime.RealtimeTopics;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.mapper.DeliveryDeadline;
import com.fixtrack.intervention.mapper.InterventionCode;
import com.fixtrack.person.entity.Person;

import lombok.RequiredArgsConstructor;

// Avisos en tiempo real de las órdenes: al personal por /topic/interventions y al cliente por el canal de su orden
@Component
@RequiredArgsConstructor
public class InterventionEventPublisher {

	private static final String INTERVENTION = "intervention";
	private static final String ORDER = "order";

	private final RealtimePublisher publisher;

	public void created(Intervention intervention) {
		publish(RealtimeEventType.CREATED, intervention);
	}

	public void statusChanged(Intervention intervention) {
		publish(RealtimeEventType.STATUS_CHANGED, intervention);
	}

	// Nueva fecha y hora de entrega: el listado recalcula el tiempo restante de esa fila y el portal la muestra
	public void rescheduled(Intervention intervention) {
		publish(RealtimeEventType.RESCHEDULED, intervention);
	}

	// Reasignación: el técnico nuevo recibe la orden y el anterior el aviso para sacarla de su lista
	public void technicianChanged(Intervention intervention, Person previousTechnician) {
		publish(RealtimeEventType.UPDATED, intervention);
		if (previousTechnician != null && (intervention.getTechnician() == null
				|| !previousTechnician.getId().equals(intervention.getTechnician().getId()))) {
			publisher.publishToUser(previousTechnician.getEmail(), RealtimeTopics.MY_INTERVENTIONS,
					RealtimeEventType.UPDATED, INTERVENTION, intervention.getId(), staffPayload(intervention));
		}
	}

	// Técnico, fecha, diagnóstico, daños, repuestos, mano de obra o pagos: cambian el detalle o el total de la orden
	public void updated(Intervention intervention) {
		publish(RealtimeEventType.UPDATED, intervention);
	}

	// Evento delgado: sin dinero, cliente ni contraseña; cada vista vuelve a pedir por REST lo que su rol puede ver.
	// Recepción lo recibe en el topic; el técnico solo el de sus órdenes, en su cola privada
	private void publish(RealtimeEventType type, Intervention intervention) {
		Map<String, Object> payload = staffPayload(intervention);
		publisher.publish(RealtimeTopics.INTERVENTIONS, type, INTERVENTION, intervention.getId(), payload);
		if (intervention.getTechnician() != null) {
			publisher.publishToUser(intervention.getTechnician().getEmail(), RealtimeTopics.MY_INTERVENTIONS, type,
					INTERVENTION, intervention.getId(), payload);
		}

		// El portal consulta con el código de la original, así que las garantías avisan también en ese canal
		Intervention original = intervention.getWarrantyOf() != null ? intervention.getWarrantyOf() : intervention;
		Map<String, Object> portalPayload = new LinkedHashMap<>();
		portalPayload.put("code", InterventionCode.of(intervention));
		portalPayload.put("status", intervention.getStatus());
		publisher.publish(RealtimeTopics.order(InterventionCode.of(original)), type, ORDER, null, portalPayload);
	}

	private Map<String, Object> staffPayload(Intervention intervention) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("code", InterventionCode.of(intervention));
		payload.put("status", intervention.getStatus());
		payload.put("technicianId", intervention.getTechnician() == null ? null : intervention.getTechnician().getId());
		payload.put("warrantyOfId", intervention.getWarrantyOf() == null ? null : intervention.getWarrantyOf().getId());
		// Con estos datos el listado actualiza solo la fila (técnico, entrega y tiempo restante) sin volver a pedirla
		payload.put("technicianName",
				intervention.getTechnician() == null ? null : intervention.getTechnician().getFullName());
		payload.put("estimatedDeliveryDate", intervention.getEstimatedDeliveryDate());
		payload.put("estimatedDeliveryTime", intervention.getEstimatedDeliveryTime());
		payload.put("estimatedDeliveryAt", DeliveryDeadline.at(intervention.getEstimatedDeliveryDate(),
				intervention.getEstimatedDeliveryTime()));
		return payload;
	}
}
