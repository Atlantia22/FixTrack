package com.fixtrack.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fixtrack.infra.realtime.RealtimeTopics;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.infra.security.JwtService;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.repository.PersonRepository;

// Prueba de punta a punta: un cliente STOMP real escucha /topic/persons y recibe el aviso de un cambio hecho por REST.
// Usa la base configurada; el cambio deja la disponibilidad del técnico igual, así que no altera datos
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private JwtService jwtService;

	private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
	private StompSession session;

	@AfterEach
	void disconnect() {
		if (session != null && session.isConnected()) {
			session.disconnect();
		}
		stompClient.stop();
	}

	@Test
	void shouldReceiveThePersonEventAfterARestChange() throws Exception {
		Person admin = activeWithRole(Role.ADMIN);
		Person technician = personRepository.findAll().stream()
				.filter(person -> person.getRole() == Role.TECHNICIAN && person.isActive()
						&& person.getAvailability() != null)
				.findFirst()
				.orElse(null);
		assumeTrue(admin != null && technician != null,
				"La base no tiene un administrador y un técnico activos con disponibilidad");
		String token = token(admin);

		BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
		session = connect(token);
		session.subscribe(RealtimeTopics.PERSONS, collector(received));
		// Da tiempo a que el broker registre la suscripción antes del cambio
		Thread.sleep(300);

		HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/people/" + technician.getId() + "/availability"))
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/json")
				.method("PATCH", HttpRequest.BodyPublishers.ofString(
						"{\"availability\":\"" + technician.getAvailability().name() + "\"}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);

		Map<String, Object> event = received.poll(5, TimeUnit.SECONDS);
		assertThat(event).isNotNull();
		assertThat(event).containsEntry("type", "UPDATED").containsEntry("entity", "person");
		assertThat(((Number) event.get("id")).longValue()).isEqualTo(technician.getId());
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = (Map<String, Object>) event.get("payload");
		assertThat(payload).doesNotContainKeys("phone", "email", "documentNumber");
	}

	@Test
	void shouldRejectAConnectionWithAnInvalidToken() {
		// El servidor responde ERROR al CONNECT y la conexión nunca llega a establecerse
		assertThatThrownBy(() -> connect("token-falso")).isInstanceOf(ExecutionException.class);
	}

	private StompSession connect(String token) throws Exception {
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		StompHeaders connectHeaders = new StompHeaders();
		connectHeaders.add(StompAuthChannelInterceptor.AUTHORIZATION_HEADER, "Bearer " + token);
		// El endpoint SockJS también acepta WebSocket nativo en /ws/websocket
		return stompClient.connectAsync("ws://localhost:" + port + "/ws/websocket", new WebSocketHttpHeaders(),
				connectHeaders, new StompSessionHandlerAdapter() {
				}).get(5, TimeUnit.SECONDS);
	}

	private StompFrameHandler collector(BlockingQueue<Map<String, Object>> received) {
		return new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((Map<String, Object>) payload);
			}
		};
	}

	private Person activeWithRole(Role role) {
		return personRepository.findAll().stream()
				.filter(person -> person.getRole() == role && person.isActive() && person.getEmail() != null)
				.findFirst()
				.orElse(null);
	}

	private String token(Person person) {
		return jwtService.issue((AuthenticatedUser) userDetailsService.loadUserByUsername(person.getEmail())).token();
	}
}
