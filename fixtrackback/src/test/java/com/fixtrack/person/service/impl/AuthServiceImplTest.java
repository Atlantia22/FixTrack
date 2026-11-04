package com.fixtrack.person.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fixtrack.common.TooManyRequestsException;
import com.fixtrack.common.UnauthorizedException;
import com.fixtrack.infra.security.IpAttemptLimiter;
import com.fixtrack.infra.security.JwtService;
import com.fixtrack.person.dto.LoginRequest;
import com.fixtrack.person.mapper.PersonMapper;
import com.fixtrack.person.repository.PersonRepository;

class AuthServiceImplTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("America/Bogota"));

	private AuthenticationManager authenticationManager;
	private AuthServiceImpl authService;

	@BeforeEach
	void setUp() {
		authenticationManager = mock(AuthenticationManager.class);
		authService = new AuthServiceImpl(authenticationManager, mock(JwtService.class), mock(PersonRepository.class),
				mock(PasswordEncoder.class), new PersonMapper(), new IpAttemptLimiter(CLOCK), CLOCK);
	}

	@Test
	void shouldBlockLoginAfterTenFailuresFromSameIp() {
		given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("mala"));
		LoginRequest request = new LoginRequest("admin@fixtrack.local", "incorrecta");

		for (int i = 0; i < 10; i++) {
			assertThatThrownBy(() -> authService.login(request, "10.0.0.5")).isInstanceOf(UnauthorizedException.class);
		}

		assertThatThrownBy(() -> authService.login(request, "10.0.0.5")).isInstanceOf(TooManyRequestsException.class);
		// Otra IP sigue pudiendo intentar
		assertThatThrownBy(() -> authService.login(request, "10.0.0.6")).isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void shouldNotCheckCredentialsWhileIpIsBlocked() {
		IpAttemptLimiter limiter = new IpAttemptLimiter(CLOCK);
		for (int i = 0; i < 10; i++) {
			limiter.recordFailure("login:10.0.0.7");
		}
		AuthServiceImpl blocked = new AuthServiceImpl(authenticationManager, mock(JwtService.class),
				mock(PersonRepository.class), mock(PasswordEncoder.class), new PersonMapper(), limiter, CLOCK);

		assertThatThrownBy(() -> blocked.login(new LoginRequest("admin@fixtrack.local", "x"), "10.0.0.7"))
				.isInstanceOf(TooManyRequestsException.class);
		verify(authenticationManager, never()).authenticate(any());
	}
}
