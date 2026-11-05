package com.fixtrack.person.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.TooManyRequestsException;
import com.fixtrack.common.UnauthorizedException;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.infra.security.IpAttemptLimiter;
import com.fixtrack.infra.security.JwtService;
import com.fixtrack.person.dto.ChangePasswordRequest;
import com.fixtrack.person.dto.LoginRequest;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.events.PersonEventPublisher;
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
				mock(PasswordEncoder.class), new PersonMapper(), new IpAttemptLimiter(CLOCK), CLOCK, mock(PersonEventPublisher.class));
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
				mock(PersonRepository.class), mock(PasswordEncoder.class), new PersonMapper(), limiter, CLOCK,
				mock(PersonEventPublisher.class));

		assertThatThrownBy(() -> blocked.login(new LoginRequest("admin@fixtrack.local", "x"), "10.0.0.7"))
				.isInstanceOf(TooManyRequestsException.class);
		verify(authenticationManager, never()).authenticate(any());
	}

	@Test
	void shouldBlockPasswordChangeAfterTenWrongCurrentPasswords() {
		PersonRepository people = mock(PersonRepository.class);
		PasswordEncoder encoder = mock(PasswordEncoder.class);
		AuthServiceImpl service = new AuthServiceImpl(authenticationManager, mock(JwtService.class), people, encoder,
				new PersonMapper(), new IpAttemptLimiter(CLOCK), CLOCK, mock(PersonEventPublisher.class));
		AuthenticatedUser actor = new AuthenticatedUser(4L, "Asesor", "asesor@fixtrack.test", Role.ADVISOR, "hash", true);
		given(people.findById(4L)).willReturn(Optional.of(Person.builder().id(4L).password("hash").build()));
		given(encoder.matches(any(), any())).willReturn(false);
		ChangePasswordRequest wrong = new ChangePasswordRequest("adivinando", "NuevaClave123");

		for (int attempt = 0; attempt < 10; attempt++) {
			assertThatThrownBy(() -> service.changePassword(actor, wrong)).isInstanceOf(BusinessRuleException.class);
		}

		// El intento 11 ni siquiera compara la contraseña
		assertThatThrownBy(() -> service.changePassword(actor, wrong)).isInstanceOf(TooManyRequestsException.class);
		verify(people, times(10)).findById(4L);
	}
}
