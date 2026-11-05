package com.fixtrack.config.websocket;

import java.time.Clock;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// Descarta los mensajes hacia una sesión cuyo token venció; el front, al recibir 401 en REST, vuelve al login
@Component
@RequiredArgsConstructor
public class ExpiredSessionOutboundInterceptor implements ChannelInterceptor {

	private final StompSessionExpirations expirations;
	private final Clock clock;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.MESSAGE
				&& expirations.isExpired(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()), clock.instant())) {
			return null;
		}
		return message;
	}
}
