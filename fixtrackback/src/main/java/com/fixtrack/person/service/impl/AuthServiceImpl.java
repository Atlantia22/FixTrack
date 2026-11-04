package com.fixtrack.person.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.NotFoundException;
import com.fixtrack.common.TooManyRequestsException;
import com.fixtrack.common.UnauthorizedException;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.infra.security.IpAttemptLimiter;
import com.fixtrack.infra.security.IssuedToken;
import com.fixtrack.infra.security.JwtService;
import com.fixtrack.person.dto.ChangePasswordRequest;
import com.fixtrack.person.dto.LoginRequest;
import com.fixtrack.person.dto.LoginResponse;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.mapper.PersonMapper;
import com.fixtrack.person.repository.PersonRepository;
import com.fixtrack.person.service.AuthService;

import lombok.RequiredArgsConstructor;

// Inicio de sesión de usuarios internos activos con token JWT y cambio de la propia contraseña
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private static final String INVALID_CREDENTIALS = "Correo o contraseña incorrectos.";
	private static final String TOO_MANY = "Demasiados intentos fallidos desde su conexión. Espere unos minutos e intente de nuevo.";
	// Los intentos del login se cuentan aparte de los del portal
	private static final String LIMITER_PREFIX = "login:";

	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final PersonRepository personRepository;
	private final PasswordEncoder passwordEncoder;
	private final PersonMapper personMapper;
	private final IpAttemptLimiter ipAttemptLimiter;
	private final Clock clock;

	// No distingue correo inexistente, cliente, usuario inactivo o contraseña errada
	@Override
	public AuthenticatedUser authenticate(String email, String password) {
		try {
			var token = UsernamePasswordAuthenticationToken.unauthenticated(email.trim().toLowerCase(Locale.ROOT),
					password);
			return (AuthenticatedUser) authenticationManager.authenticate(token).getPrincipal();
		} catch (AuthenticationException e) {
			throw new UnauthorizedException(INVALID_CREDENTIALS);
		}
	}

	// Con 10 fallos desde la misma IP en 15 minutos responde 429, sin bloquear la cuenta (así nadie deja sin acceso a otro)
	@Override
	public LoginResponse login(LoginRequest request, String clientIp) {
		String limiterKey = LIMITER_PREFIX + clientIp;
		if (ipAttemptLimiter.isBlocked(limiterKey)) {
			throw new TooManyRequestsException(TOO_MANY);
		}
		AuthenticatedUser user;
		try {
			user = authenticate(request.email(), request.password());
		} catch (UnauthorizedException e) {
			ipAttemptLimiter.recordFailure(limiterKey);
			throw e;
		}
		IssuedToken token = jwtService.issue(user);
		return new LoginResponse(token.token(), token.expiresAt(), personMapper.toAuthUser(user));
	}

	// Exige la contraseña actual y que la nueva sea distinta; revoca las demás sesiones y entrega un token nuevo
	@Override
	@Transactional
	public LoginResponse changePassword(AuthenticatedUser actor, ChangePasswordRequest request) {
		Person person = personRepository.findById(actor.id())
				.orElseThrow(() -> new NotFoundException("No existe la persona con id " + actor.id()));
		if (person.getPassword() == null || !passwordEncoder.matches(request.currentPassword(), person.getPassword())) {
			throw new BusinessRuleException("La contraseña actual no es correcta");
		}
		if (request.newPassword().equals(request.currentPassword())) {
			throw new BusinessRuleException("La nueva contraseña debe ser diferente de la actual");
		}
		person.setPassword(passwordEncoder.encode(request.newPassword()));
		person.setPasswordChangedAt(clock.instant().truncatedTo(ChronoUnit.SECONDS));
		AuthenticatedUser user = new AuthenticatedUser(person.getId(), person.getFullName(), person.getEmail(),
				person.getRole(), person.getPassword(), person.isActive(), person.getPasswordChangedAt());
		IssuedToken token = jwtService.issue(user);
		return new LoginResponse(token.token(), token.expiresAt(), personMapper.toAuthUser(user));
	}
}
