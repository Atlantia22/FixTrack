package com.fixtrack.cash.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fixtrack.cash.dto.PaymentRequest;
import com.fixtrack.cash.dto.PaymentSummaryResponse;
import com.fixtrack.cash.entity.PaymentMethod;
import com.fixtrack.cash.entity.PaymentType;
import com.fixtrack.cash.mapper.PaymentMapper;
import com.fixtrack.cash.repository.PaymentRepository;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.Money;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.service.PersonService;
import com.fixtrack.intervention.events.InterventionEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

	private static final AuthenticatedUser ADVISOR = new AuthenticatedUser(3L, "Asesora", "asesora@fixtrack.test",
			Role.ADVISOR, "hash", true);

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private InterventionPricingService pricingService;

	@Mock
	private PersonService personService;

	@Spy
	private PaymentMapper paymentMapper;

	@Mock
	private InterventionEventPublisher interventionEvents;

	@InjectMocks
	private PaymentServiceImpl paymentService;

	private Intervention intervention;

	@BeforeEach
	void setUp() {
		intervention = Intervention.builder().id(18L).status(InterventionStatus.READY).build();
		given(pricingService.getEntity(18L)).willReturn(intervention);
	}

	@Test
	void shouldRejectFinalPaymentAboveBalance() {
		given(pricingService.amountDue(intervention)).willReturn(new BigDecimal("100000.00"));
		given(paymentRepository.sumNetPaid(18L)).willReturn(new BigDecimal("30000.00"));

		assertThatThrownBy(() -> paymentService.register(18L,
				new PaymentRequest(PaymentType.FINAL, PaymentMethod.CASH, new BigDecimal("80000")), ADVISOR))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("supera el saldo pendiente de " + Money.format(new BigDecimal("70000.00")));
		verify(paymentRepository, never()).save(any());
	}

	@Test
	void shouldRejectFinalPaymentWhenOrderIsNotReady() {
		intervention.setStatus(InterventionStatus.IN_REPAIR);
		given(pricingService.amountDue(intervention)).willReturn(new BigDecimal("100000.00"));
		given(paymentRepository.sumNetPaid(18L)).willReturn(BigDecimal.ZERO);

		assertThatThrownBy(() -> paymentService.register(18L,
				new PaymentRequest(PaymentType.FINAL, PaymentMethod.CARD, new BigDecimal("1000")), ADVISOR))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("lista para entregar");
	}

	@Test
	void shouldRejectRefundWithoutCreditInFavorOfCustomer() {
		intervention.setStatus(InterventionStatus.IN_REPAIR);
		given(pricingService.amountDue(intervention)).willReturn(new BigDecimal("50000.00"));
		given(paymentRepository.sumNetPaid(18L)).willReturn(new BigDecimal("50000.00"));

		assertThatThrownBy(() -> paymentService.register(18L,
				new PaymentRequest(PaymentType.REFUND, PaymentMethod.CASH, new BigDecimal("10000")), ADVISOR))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("no tiene saldo a favor");
	}

	@Test
	void shouldAllowRefundOfDepositAfterCancellation() {
		intervention.setStatus(InterventionStatus.CANCELLED);
		given(pricingService.amountDue(intervention)).willReturn(new BigDecimal("0.00"));
		given(paymentRepository.sumNetPaid(18L)).willReturn(new BigDecimal("20000.00"), new BigDecimal("0.00"));
		given(paymentRepository.findByInterventionIdOrderByCreatedAtAscIdAsc(18L)).willReturn(List.of());

		PaymentSummaryResponse summary = paymentService.register(18L,
				new PaymentRequest(PaymentType.REFUND, PaymentMethod.CASH, new BigDecimal("20000")), ADVISOR);

		verify(paymentRepository).save(any());
		assertThat(summary.refundDue()).isEqualByComparingTo("0");
	}
}
