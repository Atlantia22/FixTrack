package com.fixtrack.config.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

// Responde el ERROR de STOMP con el motivo del rechazo (token o permiso) y no con el mensaje interno de Spring
public class StompErrorHandler extends StompSubProtocolErrorHandler {

	private static final String GENERIC = "No se pudo procesar la solicitud en tiempo real";

	@Override
	public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
		String reason = accessDeniedMessage(ex);
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
		accessor.setMessage(reason != null ? reason : GENERIC);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	// El interceptor lanza AccessDeniedException; Spring la envuelve en una MessageDeliveryException
	private String accessDeniedMessage(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof AccessDeniedException denied) {
				return denied.getMessage();
			}
		}
		return null;
	}
}
