package com.fixtrack.intervention.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fixtrack.cash.service.PaymentService;
import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.device.service.DeviceService;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.dto.CreateInterventionRequest;
import com.fixtrack.intervention.dto.RescheduleDeliveryRequest;
import com.fixtrack.intervention.entity.DeliveryDateChange;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.entity.ServiceType;
import com.fixtrack.intervention.events.InterventionEventPublisher;
import com.fixtrack.intervention.mapper.DeliveryDeadline;
import com.fixtrack.intervention.mapper.InterventionMapper;
import com.fixtrack.intervention.repository.DeliveryDateChangeRepository;
import com.fixtrack.intervention.repository.InterventionFindingRepository;
import com.fixtrack.intervention.repository.InterventionRepository;
import com.fixtrack.intervention.repository.InterventionStatusHistoryRepository;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.intervention.service.InterventionSparePartService;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.service.PersonService;
import com.fixtrack.setting.service.SettingService;

@ExtendWith(MockitoExtension.class)
class InterventionServiceImplTest {

	// 5 de noviembre de 2026, 10:00 a. m. en Bogotá
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), DeliveryDeadline.ZONE);
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
	private static final AuthenticatedUser ADVISOR = new AuthenticatedUser(3L, "Asesora", "asesora@fixtrack.test",
			Role.ADVISOR, "hash", true);

	@Mock private InterventionRepository interventionRepository;
	@Mock private InterventionFindingRepository findingRepository;
	@Mock private InterventionStatusHistoryRepository historyRepository;
	@Mock private DeliveryDateChangeRepository deliveryDateChangeRepository;
	@Mock private InterventionPricingService pricingService;
	@Mock private InterventionSparePartService sparePartService;
	@Mock private PaymentService paymentService;
	@Mock private PersonService personService;
	@Mock private DeviceService deviceService;
	@Mock private SettingService settingService;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private InterventionMapper interventionMapper;
	@Mock private ApplicationEventPublisher eventPublisher;
	@Mock private InterventionEventPublisher interventionEvents;

	private InterventionServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new InterventionServiceImpl(interventionRepository, findingRepository, historyRepository,
				deliveryDateChangeRepository, pricingService, sparePartService, paymentService, personService,
				deviceService, settingService, passwordEncoder, interventionMapper, CLOCK, eventPublisher,
				interventionEvents);
	}

	@Test
	void shouldRejectAnEstimatedDeliveryBeforeTheIntakeMoment() {
		CreateInterventionRequest request = new CreateInterventionRequest(null, null, List.of(ServiceType.OTHER),
				"No enciende", "Sin golpes", null, new BigDecimal("100000"), TODAY, LocalTime.of(9, 30), null);

		assertThatThrownBy(() -> service.create(request, ADVISOR))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("posterior al momento del ingreso");
		verifyNoInteractions(personService, interventionRepository);
	}

	@Test
	void shouldRejectAnEstimatedDeliveryAtTheSameMinuteAsTheIntake() {
		CreateInterventionRequest request = new CreateInterventionRequest(null, null, List.of(ServiceType.OTHER),
				"No enciende", "Sin golpes", null, new BigDecimal("100000"), TODAY, LocalTime.of(10, 0), null);

		assertThatThrownBy(() -> service.create(request, ADVISOR)).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void shouldRescheduleWithTimeAndPublishTheEvent() {
		Intervention order = Intervention.builder().id(18L).status(InterventionStatus.IN_REPAIR)
				.estimatedDeliveryDate(TODAY).estimatedDeliveryTime(LocalTime.of(15, 0)).build();
		given(pricingService.getEntity(18L)).willReturn(order);
		given(personService.getEntity(3L)).willReturn(Person.builder().id(3L).build());
		given(pricingService.amountDue(order)).willReturn(BigDecimal.ZERO);
		given(paymentService.netPaid(18L)).willReturn(BigDecimal.ZERO);

		service.rescheduleDelivery(18L, new RescheduleDeliveryRequest(TODAY, LocalTime.of(18, 30), "Falta un tornillo"),
				ADVISOR);

		assertThat(order.getEstimatedDeliveryTime()).isEqualTo(LocalTime.of(18, 30));
		ArgumentCaptor<DeliveryDateChange> change = ArgumentCaptor.forClass(DeliveryDateChange.class);
		verify(deliveryDateChangeRepository).save(change.capture());
		assertThat(change.getValue().getPreviousTime()).isEqualTo(LocalTime.of(15, 0));
		assertThat(change.getValue().getNewTime()).isEqualTo(LocalTime.of(18, 30));
		verify(interventionEvents).rescheduled(order);
	}

	@Test
	void shouldRejectReschedulingToAPastMomentAndNotPublish() {
		Intervention order = Intervention.builder().id(18L).status(InterventionStatus.IN_REPAIR)
				.estimatedDeliveryDate(TODAY).estimatedDeliveryTime(LocalTime.of(15, 0)).build();
		given(pricingService.getEntity(18L)).willReturn(order);

		assertThatThrownBy(() -> service.rescheduleDelivery(18L,
				new RescheduleDeliveryRequest(TODAY, LocalTime.of(8, 0), "Error"), ADVISOR))
				.isInstanceOf(BusinessRuleException.class);
		verify(deliveryDateChangeRepository, never()).save(any());
		verify(interventionEvents, never()).rescheduled(any());
	}
}
