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
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.repository.InterventionRepository;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.repository.PersonRepository;

// Prueba de punta a punta: un cliente STOMP real escucha /topic/persons y recibe el aviso de un cambio hecho por REST.
// Usa la base configurada; el cambio deja la disponibilidad del técnico igual, así que no altera datos
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RealtimeIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private UserDetailsService userDetailsService;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private InterventionRepository interventionRepository;

	private final WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
	private StompSession session;
	private StompSession otherSession;

	@AfterEach
	void disconnect() {
		for (StompSession open : new StompSession[] { session, otherSession }) {
			if (open != null && open.isConnected()) {
				open.disconnect();
			}
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

	// 4.10: el técnico recibe por su cola privada solo sus órdenes; otro técnico no recibe nada de esa orden
	@Test
	void shouldDeliverOrderEventsOnlyToTheAssignedTechnician() throws Exception {
		Person admin = activeWithRole(Role.ADMIN);
		// Fuera de una transacción el técnico de la orden es un proxy perezoso: se carga aparte por su id
		Intervention order = null;
		Person owner = null;
		for (Intervention candidate : interventionRepository.findAll()) {
			if (candidate.getTechnician() == null || candidate.getStatus().isClosed()) {
				continue;
			}
			Person technician = personRepository.findById(candidate.getTechnician().getId()).orElse(null);
			if (technician != null && technician.isActive() && technician.getEmail() != null) {
				order = candidate;
				owner = technician;
				break;
			}
		}
		Long ownerId = owner == null ? null : owner.getId();
		Person other = personRepository.findAll().stream()
				.filter(person -> person.getRole() == Role.TECHNICIAN && person.isActive() && person.getEmail() != null
						&& !person.getId().equals(ownerId))
				.findFirst()
				.orElse(null);
		assumeTrue(admin != null && order != null && other != null,
				"La base no tiene una orden abierta con técnico y otro técnico activo");

		BlockingQueue<Map<String, Object>> ownerReceived = new LinkedBlockingQueue<>();
		BlockingQueue<Map<String, Object>> otherReceived = new LinkedBlockingQueue<>();
		session = connect(token(owner));
		session.subscribe("/user/queue/interventions", collector(ownerReceived));
		otherSession = connect(token(other));
		otherSession.subscribe("/user/queue/interventions", collector(otherReceived));
		Thread.sleep(300);

		// Reasignar el mismo técnico no cambia datos y publica el aviso de la orden
		HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/interventions/" + order.getId() + "/technician"))
				.header("Authorization", "Bearer " + token(admin))
				.header("Content-Type", "application/json")
				.method("PATCH", HttpRequest.BodyPublishers.ofString("{\"technicianId\":" + owner.getId() + "}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(200);

		Map<String, Object> event = ownerReceived.poll(5, TimeUnit.SECONDS);
		assertThat(event).isNotNull();
		assertThat(((Number) event.get("id")).longValue()).isEqualTo(order.getId());
		assertThat(otherReceived.poll(2, TimeUnit.SECONDS)).isNull();
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
