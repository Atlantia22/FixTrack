package com.fixtrack.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Reglas de CORS para el front; Spring Security las aplica en su filtro de CORS
@Configuration
public class CorsConfig {

	@Bean
	public CorsConfigurationSource corsConfigurationSource(
			@Value("${fixtrack.cors.allowed-origins}") List<String> allowedOrigins) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		// X-Access-Key lleva la clave vigente al pedir el comprobante de ingreso
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Access-Key"));
		// El front lee el nombre del PDF al descargar un comprobante
		config.setExposedHeaders(List.of("Content-Disposition"));
		config.setMaxAge(3600L);

		// SockJS pide /ws/info y sus transportes con credenciales; sin Allow-Credentials el navegador bloquea la conexión.
		// La sesión del canal no usa cookies (el token va en el CONNECT), así que esto no abre la API
		CorsConfiguration realtime = new CorsConfiguration();
		realtime.setAllowedOrigins(allowedOrigins);
		realtime.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
		realtime.setAllowedHeaders(List.of("*"));
		realtime.setAllowCredentials(true);
		realtime.setMaxAge(3600L);

		// Gana la primera regla que coincide: /ws/** va antes que la general
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/ws/**", realtime);
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
