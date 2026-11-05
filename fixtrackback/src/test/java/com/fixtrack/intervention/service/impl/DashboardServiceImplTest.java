package com.fixtrack.intervention.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.dto.OrderCounters;
import com.fixtrack.intervention.dto.TechnicianWorkload;
import com.fixtrack.intervention.dto.dashboard.AdminDashboard;
import com.fixtrack.intervention.dto.dashboard.FrontDeskDashboard;
import com.fixtrack.intervention.dto.dashboard.TechnicianDashboard;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.mapper.InterventionMapper;
import com.fixtrack.intervention.repository.InterventionRepository;
import com.fixtrack.inventory.service.PartRequestService;
import com.fixtrack.inventory.service.SparePartService;
import com.fixtrack.person.dto.TechnicianSummary;
import com.fixtrack.person.entity.Availability;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.service.PersonService;
import com.fixtrack.setting.entity.SettingKey;
import com.fixtrack.setting.service.SettingService;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

	private static final OrderCounters COUNTERS = new OrderCounters(1L, 2L, 6L, 1L, 3L, 0L, 12L);

	@Mock
	private InterventionRepository interventionRepository;

	@Mock
	private InterventionMapper interventionMapper;

	@Mock
	private PersonService personService;

	@Mock
	private SparePartService sparePartService;

	@Mock
	private PartRequestService partRequestService;

	@Mock
	private SettingService settingService;

	private DashboardServiceImpl dashboardService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneId.of("America/Bogota"));
		dashboardService = new DashboardServiceImpl(interventionRepository, interventionMapper, personService,
				sparePartService, partRequestService, settingService, clock);
		given(settingService.getIntValue(SettingKey.UNCLAIMED_ALERT_DAYS)).willReturn(30);
		given(interventionRepository.findAll(any(Specification.class), any(Pageable.class)))
				.willReturn(Page.<Intervention>empty());
	}

	@Test
	void shouldGiveAdvisorTechniciansWithWorkloadButNoStockAlerts() {
		given(interventionRepository.countForDashboard(isNull(), any(), any(), any(), any(), any(), any(), any(), any(),
				any(), any())).willReturn(COUNTERS);
		given(interventionRepository.countOpenByTechnician(any())).willReturn(List.of(new TechnicianWorkload(5L, 4L)));
		given(personService.findActiveTechnicians()).willReturn(List.of(
				new TechnicianSummary(5L, "Tomás", Availability.BUSY), new TechnicianSummary(6L, "Laura", null)));

		FrontDeskDashboard dashboard = (FrontDeskDashboard) dashboardService.forUser(user(Role.ADVISOR));

		assertThat(dashboard.counters().ready()).isEqualTo(3);
		assertThat(dashboard.counters().overdue()).isEqualTo(2);
		assertThat(dashboard.technicians()).extracting("openOrders").containsExactly(4L, 0L);
		assertThat(dashboard.stockAlerts()).isNull();
		assertThat(dashboard.pendingPartRequests()).isNull();
		verify(sparePartService, never()).findLowStock();
		verify(partRequestService, never()).findAwaitingCoordinator(any(Integer.class));
	}

	@Test
	void shouldAddStockAlertsForCoordinator() {
		given(interventionRepository.countForDashboard(isNull(), any(), any(), any(), any(), any(), any(), any(), any(),
				any(), any())).willReturn(COUNTERS);
		given(sparePartService.findLowStock()).willReturn(List.of());
		given(partRequestService.countAwaitingCoordinator()).willReturn(2L);
		given(partRequestService.findAwaitingCoordinator(10)).willReturn(List.of());

		FrontDeskDashboard dashboard = (FrontDeskDashboard) dashboardService.forUser(user(Role.COORDINATOR));

		assertThat(dashboard.stockAlerts()).isEmpty();
		assertThat(dashboard.pendingPartRequestCount()).isEqualTo(2L);
		assertThat(dashboard.pendingPartRequests()).isEmpty();
	}

	@Test
	void shouldGiveAdminOrderCountersAndStockAlerts() {
		given(interventionRepository.countForDashboard(isNull(), any(), any(), any(), any(), any(), any(), any(), any(),
				any(), any())).willReturn(COUNTERS);
		given(sparePartService.findLowStock()).willReturn(List.of());

		AdminDashboard dashboard = (AdminDashboard) dashboardService.forUser(user(Role.ADMIN));

		assertThat(dashboard.counters().receivedToday()).isEqualTo(1);
		assertThat(dashboard.counters().inProgress()).isEqualTo(6);
		assertThat(dashboard.stockAlerts()).isNotNull();
	}

	@Test
	void shouldCountOnlyTechnicianOwnOrders() {
		given(interventionRepository.countForDashboard(eq(9L), any(), any(), any(), any(), any(), any(), any(), any(),
				any(), any())).willReturn(new OrderCounters(0L, 1L, 2L, 1L, 1L, 0L, 3L));

		TechnicianDashboard dashboard = (TechnicianDashboard) dashboardService.forUser(user(Role.TECHNICIAN));

		assertThat(dashboard.counters().assigned()).isEqualTo(3);
		assertThat(dashboard.counters().waitingParts()).isEqualTo(1);
		assertThat(dashboard.openOrders()).isEmpty();
	}

	private AuthenticatedUser user(Role role) {
		return new AuthenticatedUser(9L, "Prueba", "prueba@fixtrack.test", role, "hash", true);
	}
}
