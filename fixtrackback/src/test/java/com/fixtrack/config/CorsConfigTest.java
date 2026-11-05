package com.fixtrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

// El navegador pide /ws/info de SockJS con credenciales; sin Allow-Credentials no se abre el canal en tiempo real
class CorsConfigTest {

	private final CorsConfigurationSource source = new CorsConfig()
			.corsConfigurationSource(List.of("http://localhost:5173"));

	@Test
	void shouldAllowCredentialsOnlyForTheRealtimeEndpoint() {
		CorsConfiguration realtime = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/ws/info"));
		CorsConfiguration api = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/interventions"));

		assertThat(realtime.getAllowCredentials()).isTrue();
		assertThat(realtime.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
		assertThat(realtime.checkOrigin("http://evil.example")).isNull();
		assertThat(api.getAllowCredentials()).isNull();
	}
}
