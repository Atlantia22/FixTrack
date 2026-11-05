package com.fixtrack.config.websocket;

import java.security.Principal;
import java.time.Clock;
import java.util.Map;
import java.util.Set;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import com.fixtrack.common.NotFoundException;
import com.fixtrack.common.TooManyRequestsException;
import com.fixtrack.infra.realtime.RealtimeTopics;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.infra.security.JwtService;
import com.fixtrack.infra.security.JwtTokenVerifier;
import com.fixtrack.intervention.dto.portal.PortalLookupRequest;
import com.fixtrack.intervention.service.PortalService;
import com.fixtrack.person.entity.Role;

// Seguridad del canal STOMP: valida el JWT en el CONNECT y decide quién puede suscribirse a cada topic
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	static final String AUTHORIZATION_HEADER = "Authorization";
	static final String ACCESS_KEY_HEADER = "accessKey";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String DENIED = "No tiene permiso para recibir estas actualizaciones";
	private static final String PORTAL_DENIED = "No encontramos una orden con ese código y clave";

	private static final Set<Role> FRONT_DESK = Set.of(Role.ADVISOR, Role.COORDINATOR, Role.ADMIN);
	private static final Set<Role> STAFF = Set.of(Role.ADVISOR, Role.COORDINATOR, Role.ADMIN, Role.TECHNICIAN);

	// Topics internos y los roles que los ven; personas y equipos llevan datos de clientes, solo para recepción
	private static final Map<String, Set<Role>> STAFF_TOPICS = Map.of(
			RealtimeTopics.SPARE_PARTS, STAFF,
			RealtimeTopics.STOCK_ALERTS, STAFF,
			RealtimeTopics.INTERVENTIONS, STAFF,
			RealtimeTopics.PART_REQUESTS, STAFF,
			RealtimeTopics.PERSONS, FRONT_DESK,
			RealtimeTopics.DEVICES, FRONT_DESK);

	private final JwtTokenVerifier tokenVerifier;
	private final PortalService portalService;
	private final StompSessionExpirations expirations;
	private final Clock clock;

	public StompAuthChannelInterceptor(JwtService jwtService, UserDetailsService userDetailsService,
			PortalService portalService, StompSessionExpirations expirations, Clock clock) {
		this.tokenVerifier = new JwtTokenVerifier(jwtService, userDetailsService);
		this.portalService = portalService;
		this.expirations = expirations;
		this.clock = clock;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		switch (accessor.getCommand()) {
			case CONNECT -> authenticate(accessor);
			case SUBSCRIBE -> authorizeSubscription(accessor);
			// El front solo escucha: nadie envía mensajes a /app
			case SEND -> throw new AccessDeniedException(DENIED);
			case DISCONNECT -> expirations.remove(accessor.getSessionId());
			default -> {
			}
		}
		return message;
	}

	// Con token, la sesión queda a nombre del usuario hasta que el token venza; sin token solo sirve para el portal
	private void authenticate(StompHeaderAccessor accessor) {
		String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
		if (header == null || header.isBlank()) {
			return;
		}
		if (!header.startsWith(BEARER_PREFIX)) {
			throw new AccessDeniedException("El token de la sesión no es válido");
		}
		try {
			JwtTokenVerifier.VerifiedToken verified = tokenVerifier.verify(header.substring(BEARER_PREFIX.length()).trim());
			AuthenticatedUser user = verified.user();
			accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
			expirations.register(accessor.getSessionId(), verified.expiresAt());
		} catch (AuthenticationException ex) {
			throw new AccessDeniedException(ex.getMessage());
		}
	}

	private void authorizeSubscription(StompHeaderAccessor accessor) {
		String destination = accessor.getDestination();
		if (destination == null) {
			throw new AccessDeniedException(DENIED);
		}
		if (destination.startsWith(RealtimeTopics.ORDERS_PREFIX)) {
			authorizePortal(accessor, destination.substring(RealtimeTopics.ORDERS_PREFIX.length()));
			return;
		}
		Set<Role> allowed = STAFF_TOPICS.get(destination);
		AuthenticatedUser user = currentUser(accessor.getUser());
		if (allowed == null || user == null || !allowed.contains(user.role())
				|| expirations.isExpired(accessor.getSessionId(), clock.instant())) {
			throw new AccessDeniedException(DENIED);
		}
	}

	// Canal público de una orden: exige código y clave, con el mismo conteo de intentos fallidos del portal
	private void authorizePortal(StompHeaderAccessor accessor, String code) {
		String accessKey = accessor.getFirstNativeHeader(ACCESS_KEY_HEADER);
		if (accessKey == null || !accessKey.matches("\\d{4}")) {
			throw new AccessDeniedException(PORTAL_DENIED);
		}
		Map<String, Object> attributes = accessor.getSessionAttributes();
		String clientIp = attributes == null ? null : (String) attributes.get(ClientIpHandshakeInterceptor.CLIENT_IP);
		try {
			String originalCode = portalService.authorizeFollow(new PortalLookupRequest(code, accessKey),
					clientIp == null ? "desconocida" : clientIp);
			// Solo el canal de la orden original: las garantías avisan en ese mismo canal
			if (!originalCode.equals(code)) {
				throw new AccessDeniedException(PORTAL_DENIED);
			}
		} catch (NotFoundException | TooManyRequestsException ex) {
			throw new AccessDeniedException(ex.getMessage());
		}
	}

	private AuthenticatedUser currentUser(Principal principal) {
		return principal instanceof UsernamePasswordAuthenticationToken token
				&& token.getPrincipal() instanceof AuthenticatedUser user ? user : null;
	}
}
