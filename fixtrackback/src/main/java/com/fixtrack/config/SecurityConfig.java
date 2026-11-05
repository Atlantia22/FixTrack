package com.fixtrack.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.fixtrack.infra.security.JwtAuthFilter;
import com.fixtrack.infra.security.JwtService;

// Seguridad de la API REST: sin sesiones ni CSRF, con JWT, permisos por rol en cada endpoint y errores del RestControllerAdvice
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// El administrador hereda al coordinador y al técnico; el coordinador hereda todo lo del asesor
	@Bean
	static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.withDefaultRolePrefix()
				.role("ADMIN").implies("COORDINATOR", "TECHNICIAN")
				.role("COORDINATOR").implies("ADVISOR")
				.build();
	}

	// Verifica correo y contraseña contra las personas internas; lo usa el login antes de emitir el token
	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
			UserDetailsService userDetailsService,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		// Los 401 y 403 del filtro de seguridad pasan al RestControllerAdvice para responder con el mismo formato
		AuthenticationEntryPoint entryPoint = (request, response, ex) -> exceptionResolver.resolveException(request,
				response, null, ex);
		// Se crea aquí y no como bean para que el contenedor no lo registre también como filtro de servlet
		JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtService, userDetailsService, entryPoint);
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				// La API solo devuelve JSON y PDF: no carga recursos, no se enmarca y no envía el origen a terceros
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
						.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
								"camera=(), microphone=(), geolocation=(), payment=()")))
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(
								(request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex)))
				// Las rutas exigen sesión; el permiso de cada rol se define con @PreAuthorize en los controladores
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers("/api/public/**", "/error").permitAll()
						// El handshake WebSocket no lleva token; StompAuthChannelInterceptor lo valida en el CONNECT
						.requestMatchers("/ws/**").permitAll()
						.requestMatchers("/api/**").authenticated()
						.anyRequest().denyAll());
		return http.build();
	}
}
