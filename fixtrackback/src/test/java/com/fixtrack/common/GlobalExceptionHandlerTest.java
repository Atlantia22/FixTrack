package com.fixtrack.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fixtrack.config.ClockConfig;
import com.fixtrack.config.CorsConfig;
import com.fixtrack.config.SecurityConfig;
import com.fixtrack.infra.security.JwtService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.SampleController.class)
@Import({ SecurityConfig.class, CorsConfig.class, ClockConfig.class, JwtService.class,
		GlobalExceptionHandlerTest.SampleController.class })
@TestPropertySource(properties = { 
		"fixtrack.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"fixtrack.jwt.expiration=1h",
		"fixtrack.cors.allowed-origins=http://localhost:5173" // <-- ¡Añade esta línea aquí!
})
@WithMockUser
class GlobalExceptionHandlerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserDetailsService userDetailsService;

	@Test
	void shouldReturnProblemDetailForBusinessException() throws Exception {
		mockMvc.perform(get("/api/samples/missing"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("No encontrado"))
				.andExpect(jsonPath("$.detail").value("Orden no encontrada"))
				.andExpect(jsonPath("$.instance").value("/api/samples/missing"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void shouldReturnFieldErrorsInSpanishWhenBodyIsInvalid() throws Exception {
		mockMvc.perform(post("/api/samples").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Solicitud inválida"))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[0].message").value("no debe estar vacío"));
	}

	@Test
	void shouldReturnSpanishTitleForUnknownRoute() throws Exception {
		mockMvc.perform(get("/api/unknown"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("No encontrado"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void shouldAllowPreflightFromFrontOrigin() throws Exception {
		mockMvc.perform(options("/api/samples")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "Authorization, Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void shouldReturnBadRequestWhenParameterHasInvalidEncoding() throws Exception {
		mockMvc.perform(get("/api/samples/bad-encoding"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("La petición tiene caracteres no válidos; revise lo que escribió e intente de nuevo"));
	}

	@Test
	void shouldRejectPreflightFromUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/samples")
				.header("Origin", "http://evil.example")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden());
	}

	@RestController
	static class SampleController {

		record SampleRequest(@NotBlank String name) {
		}

		@GetMapping("/api/samples/missing")
		void missing() {
			throw new NotFoundException("Orden no encontrada");
		}

		// Lo que lanza Tomcat cuando un parámetro no viene en UTF-8 válido (por ejemplo, search=G%F3mez)
		@GetMapping("/api/samples/bad-encoding")
		void badEncoding() {
			throw new InvalidParameterException("Character decoding failed");
		}

		@PostMapping("/api/samples")
		void create(@Valid @RequestBody SampleRequest request) {
		}
	}
}
