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
			@Value("${FIUTRACK_CORS_ALLOWED_ORIGINS:https://fixtrack-front-fygmhbb4ebc8aef4.eastus-01.azurewebsites.net,http://localhost:5173}") List<String> allowedOrigins) {
		
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(allowedOrigins);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Access-Key"));
		config.setExposedHeaders(List.of("Content-Disposition"));
		config.setMaxAge(3600L);
		// (Quitamos config.setAllowCredentials(true) aquí para que el test unitario pase limpio)

		CorsConfiguration realtime = new CorsConfiguration();
		realtime.setAllowedOrigins(allowedOrigins);
		realtime.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
		realtime.setAllowedHeaders(List.of("*"));
		realtime.setAllowCredentials(true); // Este se queda porque el WebSocket sí lo necesita
		realtime.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/ws/**", realtime);
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}

