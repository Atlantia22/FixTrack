package com.fixtrack.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.fixtrack.common.NotFoundException;
import com.fixtrack.infra.realtime.RealtimeTopics;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.infra.security.JwtService;
import com.fixtrack.intervention.dto.portal.PortalLookupRequest;
import com.fixtrack.intervention.service.PortalService;
import com.fixtrack.person.entity.Role;

class StompAuthChannelInterceptorTest {

	private static final String SECRET = "Zml4dHJhY2stcHJ1ZWJhcy1sbGF2ZS1zZWNyZXRhLWRlLTMyLWJ5dGVz";
	private static final Instant NOW = Instant.parse("2026-11-05T15:00:00Z");

	private final Clock clock = Clock.fixed(NOW, ZoneId.of("America/Bogota"));
	private final JwtService jwtService = new JwtService(SECRET, Duration.ofHours(8), clock);
	private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
	private final PortalService portalService = mock(PortalService.class);
	private final MessageChannel channel = mock(MessageChannel.class);
	private StompSessionExpirations expirations;
	private StompAuthChannelInterceptor interceptor;

	@BeforeEach
	void setUp() {
		expirations = new StompSessionExpirations();
		interceptor = new StompAuthChannelInterceptor(jwtService, userDetailsService, portalService, expirations, clock);
	}

	@Test
	void shouldAuthenticateTheSessionWithAValidTokenOnConnect() {
		AuthenticatedUser advisor = user(3L, Role.ADVISOR);
		given(userDetailsService.loadUserByUsername(advisor.email())).willReturn(advisor);

		StompHeaderAccessor accessor = connect("Bearer " + jwtService.issue(advisor).token());
		interceptor.preSend(message(accessor), channel);

		assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
		assertThat(expirations.isExpired("s1", NOW.plus(Duration.ofHours(9)))).isTrue();
	}

	@Test
	void shouldRejectAForgedTokenOnConnect() {
		StompHeaderAccessor accessor = connect("Bearer token-falso");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void shouldLetFrontDeskSubscribeToPersonsButNotTechnicians() {
		assertThat(interceptor.preSend(subscribe(RealtimeTopics.PERSONS, user(3L, Role.ADVISOR)), channel)).isNotNull();

		assertThatThrownBy(() -> interceptor.preSend(subscribe(RealtimeTopics.PERSONS, user(5L, Role.TECHNICIAN)), channel))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void shouldRejectAnonymousSubscriptionsToStaffTopics() {
		assertThatThrownBy(() -> interceptor.preSend(subscribe(RealtimeTopics.SPARE_PARTS, null), channel))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void shouldRejectSubscriptionsAfterTheTokenExpires() {
		expirations.register("s1", NOW.minusSeconds(1));

		assertThatThrownBy(() -> interceptor.preSend(subscribe(RealtimeTopics.SPARE_PARTS, user(5L, Role.TECHNICIAN)),
				channel)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void shouldRequireTheAccessKeyToFollowAnOrderFromThePortal() {
		assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/orders/FT-00018", null), channel))
				.isInstanceOf(AccessDeniedException.class);
		verify(portalService, never()).authorizeFollow(any(), anyString());
	}

	@Test
	void shouldLetTheCustomerFollowTheOrderWithCodeAndKey() {
		given(portalService.authorizeFollow(new PortalLookupRequest("FT-00018", "3494"), "10.0.0.7")).willReturn("FT-00018");
		StompHeaderAccessor accessor = subscribeAccessor("/topic/orders/FT-00018", null);
		accessor.setNativeHeader(StompAuthChannelInterceptor.ACCESS_KEY_HEADER, "3494");

		assertThat(interceptor.preSend(message(accessor), channel)).isNotNull();
	}

	@Test
	void shouldRejectAWrongAccessKey() {
		given(portalService.authorizeFollow(any(), anyString())).willThrow(new NotFoundException("No encontramos"));
		StompHeaderAccessor accessor = subscribeAccessor("/topic/orders/FT-00018", null);
		accessor.setNativeHeader(StompAuthChannelInterceptor.ACCESS_KEY_HEADER, "0000");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void shouldRejectMessagesSentByClients() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/app/anything");

		assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
				.isInstanceOf(AccessDeniedException.class);
	}

	private AuthenticatedUser user(Long id, Role role) {
		return new AuthenticatedUser(id, "Usuario", "usuario" + id + "@fixtrack.test", role, "hash", true);
	}

	private StompHeaderAccessor connect(String authorization) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setSessionId("s1");
		accessor.setNativeHeader(StompAuthChannelInterceptor.AUTHORIZATION_HEADER, authorization);
		accessor.setLeaveMutable(true);
		return accessor;
	}

	private Message<?> subscribe(String destination, AuthenticatedUser user) {
		return message(subscribeAccessor(destination, user));
	}

	private StompHeaderAccessor subscribeAccessor(String destination, AuthenticatedUser user) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setSessionId("s1");
		accessor.setDestination(destination);
		accessor.setSessionAttributes(new HashMap<>(Map.of(ClientIpHandshakeInterceptor.CLIENT_IP, "10.0.0.7")));
		if (user != null) {
			accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
		}
		accessor.setLeaveMutable(true);
		return accessor;
	}

	private Message<?> message(StompHeaderAccessor accessor) {
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
