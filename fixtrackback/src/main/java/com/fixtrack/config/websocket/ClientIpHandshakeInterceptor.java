package com.fixtrack.config.websocket;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

// Guarda la IP del cliente en la sesión WebSocket para contar los intentos fallidos del portal, igual que en REST
public class ClientIpHandshakeInterceptor implements HandshakeInterceptor {

	static final String CLIENT_IP = "clientIp";

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		if (request.getRemoteAddress() != null) {
			attributes.put(CLIENT_IP, request.getRemoteAddress().getAddress().getHostAddress());
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
			Exception exception) {
	}
}
