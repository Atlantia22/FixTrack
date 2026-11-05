package com.fixtrack.config.websocket;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

// STOMP sobre WebSocket con SockJS de respaldo: el front se conecta a /ws y escucha los topics de /topic
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final StompAuthChannelInterceptor authInterceptor;
	private final ExpiredSessionOutboundInterceptor expiredSessionInterceptor;

	@Value("${fixtrack.cors.allowed-origins}")
	private List<String> allowedOrigins;

	// Mismos orígenes del CORS de la API (variable CORS_ALLOWED_ORIGINS); la IP se guarda para el límite del portal
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				.setAllowedOrigins(allowedOrigins.toArray(String[]::new))
				.addInterceptors(new ClientIpHandshakeInterceptor())
				.withSockJS();
		registry.setErrorHandler(new StompErrorHandler());
	}

	// Broker en memoria para /topic; /app queda reservado aunque hoy el front solo escucha
	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}

	// Entrada: valida el token en el CONNECT y el permiso de cada SUBSCRIBE
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(authInterceptor);
	}

	// Salida: no entrega mensajes a una sesión cuyo token ya venció
	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.interceptors(expiredSessionInterceptor);
	}
}
