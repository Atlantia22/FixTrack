package com.fixtrack.infra.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class RealtimeBroadcasterTest {

	@Mock
	private SimpMessagingTemplate messagingTemplate;

	@InjectMocks
	private RealtimeBroadcaster broadcaster;

	private final RealtimeEvent event = new RealtimeEvent(RealtimeEventType.UPDATED, "person", 3L, Map.of(),
			Instant.parse("2026-11-05T15:00:00Z"));

	@Test
	void shouldSendTheEventToItsTopic() {
		broadcaster.broadcast(new RealtimeMessage(RealtimeTopics.PERSONS, event));

		verify(messagingTemplate).convertAndSend(RealtimeTopics.PERSONS, event);
	}

	@Test
	void shouldListenOnlyAfterTheTransactionCommits() throws NoSuchMethodException {
		TransactionalEventListener listener = RealtimeBroadcaster.class
				.getMethod("broadcast", RealtimeMessage.class).getAnnotation(TransactionalEventListener.class);

		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	@Test
	void shouldNotFailTheCallerWhenTheBrokerIsUnavailable() {
		willThrow(new MessageDeliveryException("sin broker")).given(messagingTemplate)
				.convertAndSend(anyString(), any(Object.class));

		assertThatCode(() -> broadcaster.broadcast(new RealtimeMessage(RealtimeTopics.PERSONS, event)))
				.doesNotThrowAnyException();
	}
}
