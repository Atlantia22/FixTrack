package com.fixtrack.infra.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Autentica cada petición con el token Bearer; sin token sigue anónima y las rutas protegidas responden 401
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenVerifier tokenVerifier;
	private final AuthenticationEntryPoint entryPoint;

	public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService,
			AuthenticationEntryPoint entryPoint) {
		this.tokenVerifier = new JwtTokenVerifier(jwtService, userDetailsService);
		this.entryPoint = entryPoint;
	}

	// El login, el portal público y el canal WebSocket no usan este filtro (el WebSocket valida el token en el CONNECT)
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.equals("/api/auth/login") || path.startsWith("/api/public/") || path.startsWith("/ws");
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
			AuthenticatedUser user = tokenVerifier.verify(header.substring(BEARER_PREFIX.length()).trim()).user();
			SecurityContextHolder.getContext().setAuthentication(
					UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
		} catch (AuthenticationException ex) {
			SecurityContextHolder.clearContext();
			entryPoint.commence(request, response, ex);
			return;
		}
		chain.doFilter(request, response);
	}
}
