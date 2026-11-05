package com.fixtrack.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.infra.security.JwtService;
import com.fixtrack.person.entity.Role;

@WebMvcTest(controllers = SecurityConfigTest.SampleController.class)
@Import({ SecurityConfig.class, CorsConfig.class, ClockConfig.class, JwtService.class,
		SecurityConfigTest.SampleController.class })
@TestPropertySource(properties = { "fixtrack.jwt.secret=" + SecurityConfigTest.SECRET, "fixtrack.jwt.expiration=1h" })
class SecurityConfigTest {

	static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
	private static final String OTHER_SECRET = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private UserDetailsService userDetailsService;

	@Test
	void shouldRejectRequestWithoutToken() throws Exception {
		mockMvc.perform(get("/api/samples/advisor"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Debe iniciar sesión para acceder a este recurso"));
	}

	@Test
	void shouldRejectBasicCredentials() throws Exception {
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, "Basic YTpi"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldSendSecurityHeadersOnEveryResponse() throws Exception {
		mockMvc.perform(get("/api/samples/advisor"))
				.andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
				.andExpect(header().string("Referrer-Policy", "no-referrer"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().exists("Permissions-Policy"));
	}

	@Test
	void shouldAllowAdvisorWithValidToken() throws Exception {
		AuthenticatedUser advisor = registered(1L, "asesor@fixtrack.test", Role.ADVISOR, true);
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, bearer(advisor)))
				.andExpect(status().isOk());
	}

	@Test
	void shouldLetCoordinatorInheritAdvisorPermissions() throws Exception {
		AuthenticatedUser coordinator = registered(2L, "coordinador@fixtrack.test", Role.COORDINATOR, true);
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, bearer(coordinator)))
				.andExpect(status().isOk());
	}

	@Test
	void shouldForbidTechnicianOnAdvisorEndpoint() throws Exception {
		AuthenticatedUser technician = registered(3L, "tecnico@fixtrack.test", Role.TECHNICIAN, true);
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, bearer(technician)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("No tiene permiso para realizar esta acción"));
	}

	@Test
	void shouldForbidAdvisorOnCoordinatorEndpoint() throws Exception {
		AuthenticatedUser advisor = registered(1L, "asesor@fixtrack.test", Role.ADVISOR, true);
		mockMvc.perform(get("/api/samples/coordinator").header(HttpHeaders.AUTHORIZATION, bearer(advisor)))
				.andExpect(status().isForbidden());
	}

	@Test
	void shouldRejectExpiredToken() throws Exception {
		AuthenticatedUser advisor = registered(1L, "asesor@fixtrack.test", Role.ADVISOR, true);
		String expired = new JwtService(SECRET, Duration.ofHours(1),
				Clock.offset(Clock.systemUTC(), Duration.ofHours(-2))).issue(advisor).token();
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("La sesión expiró o no es válida; inicie sesión de nuevo"));
	}

	@Test
	void shouldRejectTokenSignedWithAnotherKey() throws Exception {
		AuthenticatedUser advisor = registered(1L, "asesor@fixtrack.test", Role.ADVISOR, true);
		String forged = new JwtService(OTHER_SECRET, Duration.ofHours(1), Clock.systemUTC()).issue(advisor).token();
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldRejectTokenOfDeactivatedUser() throws Exception {
		AuthenticatedUser advisor = registered(1L, "asesor@fixtrack.test", Role.ADVISOR, true);
		String token = bearer(advisor);
		registered(1L, "asesor@fixtrack.test", Role.ADVISOR, false);
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Su usuario fue desactivado o cambió de rol; inicie sesión de nuevo"));
	}

	@Test
	void shouldRejectTokenWhenRoleChanged() throws Exception {
		AuthenticatedUser advisor = registered(1L, "asesor@fixtrack.test", Role.ADVISOR, true);
		String token = bearer(advisor);
		registered(1L, "asesor@fixtrack.test", Role.TECHNICIAN, true);
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldRejectTokenIssuedBeforePasswordChange() throws Exception {
		AuthenticatedUser advisor = registered(1L, "asesor@fixtrack.test", Role.ADVISOR, true);
		String token = bearer(advisor);
		given(userDetailsService.loadUserByUsername("asesor@fixtrack.test")).willReturn(new AuthenticatedUser(1L,
				"Prueba", "asesor@fixtrack.test", Role.ADVISOR, "hash", true, Instant.now().plusSeconds(60)));
		mockMvc.perform(get("/api/samples/advisor").header(HttpHeaders.AUTHORIZATION, token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Su contraseña cambió; inicie sesión de nuevo"));
	}

	@Test
	void shouldOpenPublicRoutesEvenWithInvalidToken() throws Exception {
		mockMvc.perform(get("/api/public/samples")).andExpect(status().isOk());
		mockMvc.perform(get("/api/public/samples").header(HttpHeaders.AUTHORIZATION, "Bearer basura"))
				.andExpect(status().isOk());
	}

	private AuthenticatedUser registered(Long id, String email, Role role, boolean enabled) {
		AuthenticatedUser user = new AuthenticatedUser(id, "Prueba", email, role, "hash", enabled);
		given(userDetailsService.loadUserByUsername(email)).willReturn(user);
		return user;
	}

	private String bearer(AuthenticatedUser user) {
		return "Bearer " + jwtService.issue(user).token();
	}

	@RestController
	static class SampleController {

		@GetMapping("/api/samples/advisor")
		@PreAuthorize("hasRole('ADVISOR')")
		void advisor() {
		}

		@GetMapping("/api/samples/coordinator")
		@PreAuthorize("hasRole('COORDINATOR')")
		void coordinator() {
		}

		@GetMapping("/api/public/samples")
		void publicSample() {
		}
	}
}
