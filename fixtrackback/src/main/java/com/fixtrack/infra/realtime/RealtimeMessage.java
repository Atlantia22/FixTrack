package com.fixtrack.infra.realtime;

// Evento de Spring que viaja dentro de la transacción y se envía solo cuando esta se confirma.
// Sin user va a un topic compartido; con user (su correo) va a la cola privada de esa persona
public record RealtimeMessage(String destination, RealtimeEvent event, String user) {

	public RealtimeMessage(String destination, RealtimeEvent event) {
		this(destination, event, null);
	}
}
