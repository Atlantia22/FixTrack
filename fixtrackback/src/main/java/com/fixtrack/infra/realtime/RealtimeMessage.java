package com.fixtrack.infra.realtime;

// Evento de Spring que viaja dentro de la transacción y se envía al topic solo cuando esta se confirma
public record RealtimeMessage(String destination, RealtimeEvent event) {
}
