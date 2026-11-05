package com.fixtrack.intervention.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fixtrack.infra.realtime.RealtimeEventType;
import com.fixtrack.infra.realtime.RealtimePublisher;
import com.fixtrack.infra.realtime.RealtimeTopics;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.person.entity.Person;

@ExtendWith(MockitoExtension.class)
class InterventionEventPublisherTest {

	@Mock
	private RealtimePublisher publisher;

	private InterventionEventPublisher events;

	@BeforeEach
	void setUp() {
		events = new InterventionEventPublisher(publisher);
	}

	@Test
	void shouldPublishStatusChangeToStaffAndToThePortalChannelWithoutSensitiveData() {
		Intervention order = Intervention.builder().id(18L).status(InterventionStatus.READY)
				.technician(Person.builder().id(5L).build()).unlockCode("1-5-9-6")
				.laborCost(new BigDecimal("50000")).build();

		events.statusChanged(order);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> staff = ArgumentCaptor.forClass(Map.class);
		verify(publisher).publish(eq(RealtimeTopics.INTERVENTIONS), eq(RealtimeEventType.STATUS_CHANGED),
				eq("intervention"), eq(18L), staff.capture());
		assertThat(staff.getValue()).containsEntry("code", "FT-00018").containsEntry("status", InterventionStatus.READY)
				.containsEntry("technicianId", 5L).doesNotContainKeys("unlockCode", "laborCost", "total");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> portal = ArgumentCaptor.forClass(Map.class);
		verify(publisher).publish(eq("/topic/orders/FT-00018"), eq(RealtimeEventType.STATUS_CHANGED), eq("order"),
				isNull(), portal.capture());
		assertThat(portal.getValue()).containsOnlyKeys("code", "status");
	}

	@Test
	void shouldSendTheOrderOnlyToItsTechnicianPrivateQueue() {
		Intervention order = Intervention.builder().id(18L).status(InterventionStatus.IN_REPAIR)
				.technician(Person.builder().id(5L).email("tito@fixtrack.test").build()).build();

		events.updated(order);

		verify(publisher).publishToUser(eq("tito@fixtrack.test"), eq(RealtimeTopics.MY_INTERVENTIONS),
				eq(RealtimeEventType.UPDATED), eq("intervention"), eq(18L), anyMap());
	}

	@Test
	void shouldTellThePreviousTechnicianWhenTheOrderIsReassigned() {
		Person previous = Person.builder().id(5L).email("tito@fixtrack.test").build();
		Intervention order = Intervention.builder().id(18L).status(InterventionStatus.IN_REPAIR)
				.technician(Person.builder().id(6L).email("lia@fixtrack.test").build()).build();

		events.technicianChanged(order, previous);

		verify(publisher).publishToUser(eq("lia@fixtrack.test"), eq(RealtimeTopics.MY_INTERVENTIONS), any(),
				eq("intervention"), eq(18L), anyMap());
		verify(publisher).publishToUser(eq("tito@fixtrack.test"), eq(RealtimeTopics.MY_INTERVENTIONS), any(),
				eq("intervention"), eq(18L), anyMap());
	}

	@Test
	void shouldNotifyWarrantyChangesOnTheOriginalOrderChannel() {
		Intervention original = Intervention.builder().id(6L).status(InterventionStatus.DELIVERED).build();
		Intervention warranty = Intervention.builder().id(7L).status(InterventionStatus.IN_REPAIR).warrantyOf(original)
				.warrantyNumber(1).build();

		events.created(warranty);

		verify(publisher).publish(eq(RealtimeTopics.order("FT-00006")), eq(RealtimeEventType.CREATED), eq("order"),
				isNull(), anyMap());
	}
}
