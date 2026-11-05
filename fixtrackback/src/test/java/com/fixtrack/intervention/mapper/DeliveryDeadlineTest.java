package com.fixtrack.intervention.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.fixtrack.intervention.entity.InterventionStatus;

class DeliveryDeadlineTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
	private static final LocalDateTime NOW = TODAY.atTime(10, 0);

	@Test
	void shouldBeOverdueFromTheMinuteTheAgreedTimePasses() {
		assertThat(DeliveryDeadline.isOverdue(InterventionStatus.IN_REPAIR, TODAY, LocalTime.of(9, 59), NOW)).isTrue();
		assertThat(DeliveryDeadline.isOverdue(InterventionStatus.IN_REPAIR, TODAY, LocalTime.of(10, 0), NOW)).isTrue();
		assertThat(DeliveryDeadline.isOverdue(InterventionStatus.IN_REPAIR, TODAY, LocalTime.of(10, 1), NOW)).isFalse();
	}

	@Test
	void shouldNotBeOverdueOnceReadyDeliveredOrCancelled() {
		LocalTime past = LocalTime.of(8, 0);
		assertThat(DeliveryDeadline.isOverdue(InterventionStatus.READY, TODAY, past, NOW)).isFalse();
		assertThat(DeliveryDeadline.isOverdue(InterventionStatus.DELIVERED, TODAY, past, NOW)).isFalse();
		assertThat(DeliveryDeadline.isOverdue(InterventionStatus.CANCELLED, TODAY, past, NOW)).isFalse();
	}

	@Test
	void shouldKeepTheDayRuleForOldOrdersWithoutTime() {
		assertThat(DeliveryDeadline.isOverdue(InterventionStatus.IN_REPAIR, TODAY, null, NOW)).isFalse();
		assertThat(DeliveryDeadline.isOverdue(InterventionStatus.IN_REPAIR, TODAY.minusDays(1), null, NOW)).isTrue();
	}

	@Test
	void shouldExposeTheMomentWithTheBogotaOffset() {
		assertThat(DeliveryDeadline.at(TODAY, LocalTime.of(15, 30))).hasToString("2026-11-05T15:30-05:00");
		assertThat(DeliveryDeadline.at(TODAY, null)).isNull();
	}
}
