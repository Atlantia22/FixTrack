package com.fixtrack.intervention.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.dto.InterventionFilter;
import com.fixtrack.intervention.dto.InterventionSummaryResponse;
import com.fixtrack.intervention.dto.OrderCounters;
import com.fixtrack.intervention.dto.TechnicianWorkload;
import com.fixtrack.intervention.dto.dashboard.AdminDashboard;
import com.fixtrack.intervention.dto.dashboard.AdminDashboard.AdminCounters;
import com.fixtrack.intervention.dto.dashboard.DashboardResponse;
import com.fixtrack.intervention.dto.dashboard.FrontDeskDashboard;
import com.fixtrack.intervention.dto.dashboard.FrontDeskDashboard.FrontDeskCounters;
import com.fixtrack.intervention.dto.dashboard.StockAlertResponse;
import com.fixtrack.intervention.dto.dashboard.TechnicianDashboard;
import com.fixtrack.intervention.dto.dashboard.TechnicianDashboard.TechnicianCounters;
import com.fixtrack.intervention.dto.dashboard.TechnicianStatusResponse;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.mapper.InterventionMapper;
import com.fixtrack.intervention.repository.InterventionRepository;
import com.fixtrack.intervention.repository.InterventionSpecifications;
import com.fixtrack.intervention.service.DashboardService;
import com.fixtrack.inventory.service.PartRequestService;
import com.fixtrack.inventory.service.SparePartService;
import com.fixtrack.person.service.PersonService;
import com.fixtrack.setting.entity.SettingKey;
import com.fixtrack.setting.service.SettingService;

import lombok.RequiredArgsConstructor;

// Panel de Inicio según el rol: contadores con una consulta agregada, órdenes recientes, técnicos y alertas de stock
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

	private static final int RECENT_ORDERS = 10;
	private static final int TECHNICIAN_OPEN_ORDERS = 50;
	private static final int PENDING_PART_REQUESTS = 10;
	private static final Set<InterventionStatus> OVERDUE_CANDIDATES = EnumSet.of(InterventionStatus.RECEIVED,
			InterventionStatus.IN_DIAGNOSIS, InterventionStatus.WAITING_PARTS, InterventionStatus.IN_REPAIR);
	private static final Set<InterventionStatus> IN_PROGRESS = EnumSet.of(InterventionStatus.IN_DIAGNOSIS,
			InterventionStatus.WAITING_PARTS, InterventionStatus.IN_REPAIR);
	private static final Set<InterventionStatus> CLOSED = EnumSet.of(InterventionStatus.DELIVERED,
			InterventionStatus.CANCELLED);

	private final InterventionRepository interventionRepository;
	private final InterventionMapper interventionMapper;
	private final PersonService personService;
	private final SparePartService sparePartService;
	private final PartRequestService partRequestService;
	private final SettingService settingService;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public DashboardResponse forUser(AuthenticatedUser user) {
		return switch (user.role()) {
			case ADMIN -> adminDashboard(user);
			case COORDINATOR -> frontDeskDashboard(user, true);
			case ADVISOR -> frontDeskDashboard(user, false);
			case TECHNICIAN -> technicianDashboard(user);
			case CUSTOMER -> throw new IllegalStateException("Los clientes no inician sesión");
		};
	}

	// Administrador: hoy, atrasadas, en proceso y listas; órdenes recientes y repuestos en alerta
	private AdminDashboard adminDashboard(AuthenticatedUser user) {
		OrderCounters counters = countOrders(null);
		return new AdminDashboard(user.role(),
				new AdminCounters(counters.receivedToday(), counters.overdue(), counters.inProgress(), counters.ready()),
				recentOrders(), stockAlerts());
	}

	// Asesor y coordinador: listas, ingresadas hoy, atrasadas, en proceso y no reclamadas; recientes y técnicos.
	// El coordinador además ve las alertas de stock y las solicitudes de repuestos que esperan su gestión
	private FrontDeskDashboard frontDeskDashboard(AuthenticatedUser user, boolean coordinator) {
		OrderCounters counters = countOrders(null);
		return new FrontDeskDashboard(user.role(),
				new FrontDeskCounters(counters.ready(), counters.receivedToday(), counters.overdue(),
						counters.inProgress(), counters.unclaimed()),
				recentOrders(), technicians(), coordinator ? stockAlerts() : null,
				coordinator ? partRequestService.countAwaitingCoordinator() : null,
				coordinator ? partRequestService.findAwaitingCoordinator(PENDING_PART_REQUESTS) : null);
	}

	// Técnico: sus órdenes abiertas, atrasadas, en proceso y esperando repuestos, y la lista por fecha de entrega
	private TechnicianDashboard technicianDashboard(AuthenticatedUser user) {
		OrderCounters counters = countOrders(user.id());
		LocalDateTime now = LocalDateTime.now(clock);
		InterventionFilter mine = InterventionFilter.ofTechnician(user.id(), null, null, false);
		List<InterventionSummaryResponse> openOrders = interventionRepository
				.findAll(InterventionSpecifications.matching(mine, null, now, null), PageRequest.of(0,
						TECHNICIAN_OPEN_ORDERS, Sort.by("estimatedDeliveryDate", "estimatedDeliveryTime", "receivedAt")))
				.map(intervention -> interventionMapper.toSummary(intervention, now))
				.getContent();
		return new TechnicianDashboard(user.role(),
				new TechnicianCounters(counters.open(), counters.overdue(), counters.inProgress(),
						counters.waitingParts()),
				openOrders);
	}

	// No reclamadas: listas desde hace más de UNCLAIMED_ALERT_DAYS días
	private OrderCounters countOrders(Long technicianId) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate today = now.toLocalDate();
		LocalDateTime unclaimedBefore = now.minusDays(settingService.getIntValue(SettingKey.UNCLAIMED_ALERT_DAYS));
		// Atrasadas al minuto: la hora actual se compara con la hora acordada de las que vencen hoy
		return interventionRepository.countForDashboard(technicianId, today.atStartOfDay(),
				today.plusDays(1).atStartOfDay(), today, now.toLocalTime(), unclaimedBefore, OVERDUE_CANDIDATES,
				IN_PROGRESS, CLOSED,
				InterventionStatus.WAITING_PARTS, InterventionStatus.READY);
	}

	private List<InterventionSummaryResponse> recentOrders() {
		LocalDateTime now = LocalDateTime.now(clock);
		InterventionFilter all = new InterventionFilter(null, null, null, null, null, null, null, null, false, false, false);
		return interventionRepository
				.findAll(InterventionSpecifications.matching(all, null, now, null),
						PageRequest.of(0, RECENT_ORDERS, Sort.by(Sort.Direction.DESC, "receivedAt", "id")))
				.map(intervention -> interventionMapper.toSummary(intervention, now))
				.getContent();
	}

	// Técnicos activos con su disponibilidad y cuántas órdenes abiertas tienen
	private List<TechnicianStatusResponse> technicians() {
		Map<Long, Long> workload = interventionRepository.countOpenByTechnician(CLOSED).stream()
				.collect(Collectors.toMap(TechnicianWorkload::technicianId, TechnicianWorkload::openOrders));
		return personService.findActiveTechnicians().stream()
				.map(technician -> new TechnicianStatusResponse(technician.id(), technician.fullName(),
						technician.availability(), workload.getOrDefault(technician.id(), 0L)))
				.toList();
	}

	private List<StockAlertResponse> stockAlerts() {
		return sparePartService.findLowStock().stream()
				.map(part -> new StockAlertResponse(part.id(), part.name(), part.sku(),
						part.brandName() + " " + part.deviceModelName(), part.stock(), part.minStock(),
						part.stockStatus()))
				.toList();
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}
}
