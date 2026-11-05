package com.fixtrack.cash.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.common.SortWhitelist;
import com.fixtrack.cash.dto.CashClosingRequest;
import com.fixtrack.cash.dto.CashClosingResponse;
import com.fixtrack.cash.dto.CashTodayResponse;
import com.fixtrack.cash.dto.MethodTotal;
import com.fixtrack.cash.entity.CashClosing;
import com.fixtrack.cash.entity.PaymentMethod;
import com.fixtrack.cash.mapper.CashClosingMapper;
import com.fixtrack.cash.repository.CashClosingRepository;
import com.fixtrack.cash.repository.PaymentRepository;
import com.fixtrack.cash.service.CashClosingService;
import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.common.PageResponse;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.person.service.PersonService;

import lombok.RequiredArgsConstructor;

// Cierre de caja diario de cada usuario: lo esperado por medio de pago contra el efectivo contado; no se edita
@Service
@RequiredArgsConstructor
public class CashClosingServiceImpl implements CashClosingService {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	// Campos por los que el front puede ordenar el listado; cualquier otro responde 400
	private static final Map<String, String> SORTABLE = Map.of(
			"closingDate", "closingDate",
			"id", "id");

	private final CashClosingRepository cashClosingRepository;
	private final PaymentRepository paymentRepository;
	private final PersonService personService;
	private final CashClosingMapper cashClosingMapper;
	private final Clock clock;

	// Esperado del día: pagos del usuario aún sin cierre (incluye los de días sin cerrar), devoluciones restando
	@Override
	@Transactional(readOnly = true)
	public CashTodayResponse today(AuthenticatedUser actor) {
		LocalDate today = LocalDate.now(clock);
		List<MethodTotal> pending = paymentRepository.sumPendingByMethod(actor.id(), today.atStartOfDay(),
				today.plusDays(1).atStartOfDay());
		Map<PaymentMethod, BigDecimal> byMethod = byMethod(pending);
		CashClosingResponse closing = cashClosingRepository.findByClosedByIdAndClosingDate(actor.id(), today)
				.map(found -> cashClosingMapper.toResponse(found, paymentRepository.countByCashClosingId(found.getId())))
				.orElse(null);
		return new CashTodayResponse(today, byMethod.get(PaymentMethod.CASH), byMethod.get(PaymentMethod.TRANSFER),
				byMethod.get(PaymentMethod.CARD), total(byMethod),
				pending.stream().mapToLong(MethodTotal::payments).sum(),
				pending.stream().mapToLong(MethodTotal::fromPreviousDays).sum(), closing);
	}

	// Primero se ligan los pagos y luego se suman los ligados: un pago que entre a la vez no queda contado a medias
	@Override
	@Transactional
	public CashClosingResponse close(CashClosingRequest request, AuthenticatedUser actor) {
		LocalDate today = LocalDate.now(clock);
		if (cashClosingRepository.existsByClosedByIdAndClosingDate(actor.id(), today)) {
			throw new ConflictException("Ya hizo el cierre de caja de hoy; un cierre no se edita");
		}

		// 1. Cierre con lo contado y pagos pendientes ligados
		CashClosing closing = cashClosingRepository.save(CashClosing.builder()
				.closedBy(personService.getEntity(actor.id()))
				.closingDate(today)
				.expectedCash(ZERO)
				.expectedTransfer(ZERO)
				.expectedCard(ZERO)
				.countedCash(request.countedCash().setScale(2, RoundingMode.HALF_UP))
				.notes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim())
				.build());
		int linked = paymentRepository.linkPendingToClosing(closing, actor.id(), today.plusDays(1).atStartOfDay());
		if (linked == 0 && closing.getCountedCash().signum() > 0) {
			throw new BusinessRuleException("No tiene pagos pendientes de cierre; el efectivo contado debe ser 0");
		}

		// 2. Esperado por medio de pago a partir de los pagos ligados
		CashClosing saved = cashClosingRepository.findById(closing.getId()).orElseThrow();
		Map<PaymentMethod, BigDecimal> byMethod = byMethod(paymentRepository.sumByClosing(saved.getId()));
		saved.setExpectedCash(byMethod.get(PaymentMethod.CASH));
		saved.setExpectedTransfer(byMethod.get(PaymentMethod.TRANSFER));
		saved.setExpectedCard(byMethod.get(PaymentMethod.CARD));
		return cashClosingMapper.toResponse(saved, linked);
	}

	@Override
	@Transactional(readOnly = true)
	public PageResponse<CashClosingResponse> search(Long closedById, LocalDate from, LocalDate to, Pageable pageable,
			AuthenticatedUser actor) {
		pageable = SortWhitelist.restrict(pageable, SORTABLE);
		if (from != null && to != null && from.isAfter(to)) {
			throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
		}
		Long owner = actor.isAdmin() ? closedById : actor.id();
		return PageResponse.from(cashClosingRepository.search(owner, from, to, pageable)
				.map(closing -> cashClosingMapper.toResponse(closing,
						paymentRepository.countByCashClosingId(closing.getId()))));
	}

	private Map<PaymentMethod, BigDecimal> byMethod(List<MethodTotal> totals) {
		Map<PaymentMethod, BigDecimal> byMethod = new EnumMap<>(PaymentMethod.class);
		for (PaymentMethod method : PaymentMethod.values()) {
			byMethod.put(method, ZERO);
		}
		totals.forEach(total -> byMethod.put(total.method(), total.amount().setScale(2, RoundingMode.HALF_UP)));
		return byMethod;
	}

	private BigDecimal total(Map<PaymentMethod, BigDecimal> byMethod) {
		return byMethod.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
	}
}
