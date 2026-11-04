package com.fixtrack.infra.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// Autentica cada petición con el token Bearer; sin token sigue anónima y las rutas protegidas responden 401
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String INVALID_SESSION = "La sesión expiró o no es válida; inicie sesión de nuevo";
	private static final String USER_CHANGED = "Su usuario fue desactivado o cambió de rol; inicie sesión de nuevo";
	private static final String PASSWORD_CHANGED = "Su contraseña cambió; inicie sesión de nuevo";

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final AuthenticationEntryPoint entryPoint;

	// El login y el portal público no usan token, aunque el navegador envíe uno viejo
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.equals("/api/auth/login") || path.startsWith("/api/public/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			chain.doFilter(request, response);
			return;
		}
		try {
			AuthenticatedUser user = verify(header.substring(BEARER_PREFIX.length()).trim());
			SecurityContextHolder.getContext().setAuthentication(
					UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
		} catch (AuthenticationException ex) {
			SecurityContextHolder.clearContext();
			entryPoint.commence(request, response, ex);
			return;
		}
		chain.doFilter(request, response);
	}

	// Valida firma y expiración, que el usuario siga activo con el mismo rol y que no haya cambiado su contraseña
	private AuthenticatedUser verify(String token) {
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
		return user;
	}
}
