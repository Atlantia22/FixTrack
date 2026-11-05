package com.fixtrack.infra.security;

import java.time.Instant;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

// Reglas de un token válido, compartidas por las peticiones REST (JwtAuthFilter) y la conexión WebSocket
public class JwtTokenVerifier {

	static final String INVALID_SESSION = "La sesión expiró o no es válida; inicie sesión de nuevo";
	static final String USER_CHANGED = "Su usuario fue desactivado o cambió de rol; inicie sesión de nuevo";
	static final String PASSWORD_CHANGED = "Su contraseña cambió; inicie sesión de nuevo";

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	public JwtTokenVerifier(JwtService jwtService, UserDetailsService userDetailsService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	public record VerifiedToken(AuthenticatedUser user, Instant expiresAt) {
	}

	// Valida firma y expiración, que el usuario siga activo con el mismo rol y que no haya cambiado su contraseña
	public VerifiedToken verify(String token) {
		Jwt jwt;
		try {
			jwt = jwtService.decode(token);
		} catch (JwtException ex) {
			throw new InvalidTokenException(INVALID_SESSION);
		}
		AuthenticatedUser user;
		try {
			user = (AuthenticatedUser) userDetailsService.loadUserByUsername(jwt.getSubject());
		} catch (UsernameNotFoundException ex) {
			throw new InvalidTokenException(USER_CHANGED);
		}
		boolean sameUser = jwt.getClaim(JwtService.USER_ID_CLAIM) instanceof Number id && id.longValue() == user.id();
		if (!user.isEnabled() || !sameUser || !user.role().name().equals(jwt.getClaimAsString(JwtService.ROLE_CLAIM))) {
			throw new InvalidTokenException(USER_CHANGED);
		}
		// Un token emitido antes del último cambio de contraseña queda revocado
		if (user.passwordChangedAt() != null
				&& (jwt.getIssuedAt() == null || jwt.getIssuedAt().isBefore(user.passwordChangedAt()))) {
			throw new InvalidTokenException(PASSWORD_CHANGED);
		}
		return new VerifiedToken(user, jwt.getExpiresAt());
	}
}
