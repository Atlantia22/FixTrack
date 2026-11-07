package com.fixtrack.intervention.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.dto.report.MaintenanceReport;
import com.fixtrack.intervention.dto.report.OrderProfitRow;
import com.fixtrack.intervention.dto.report.ProfitReport;
import com.fixtrack.intervention.dto.report.StatusCount;
import com.fixtrack.intervention.dto.report.TechnicianCost;
import com.fixtrack.intervention.dto.report.TechnicianRevenue;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.repository.InterventionRepository;
import com.fixtrack.intervention.repository.InterventionSparePartRepository;
import com.fixtrack.person.entity.Role;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

	private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
	private static final LocalDate TO = LocalDate.of(2026, 10, 31);
	private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "Admin", "admin@fixtrack.test",
			Role.ADMIN, "hash", true);
	private static final AuthenticatedUser TECHNICIAN = new AuthenticatedUser(5L, "Tomás", "tomas@fixtrack.test",
			Role.TECHNICIAN, "hash", true);

	@Mock
	private InterventionRepository interventionRepository;

	@Mock
	private InterventionSparePartRepository usageRepository;

	@Mock
	private ProfitExcelExporter profitExcelExporter;

	@InjectMocks
	private ReportServiceImpl reportService;

	@Test
	void shouldCombineRevenueAndCostPerTechnicianIncludingWarranties() {
		given(usageRepository.sumCostByTechnician(any(), any(), isNull(), eq(InterventionStatus.DELIVERED)))
				.willReturn(List.of(new TechnicianCost(5L, new BigDecimal("330000"), new BigDecimal("30000"))));
		given(interventionRepository.sumRevenueByTechnician(any(), any(), isNull(), eq(InterventionStatus.DELIVERED)))
				.willReturn(List.of(new TechnicianRevenue(5L, "Tomás", 3L, new BigDecimal("685000"), 1L),
						new TechnicianRevenue(null, null, 1L, new BigDecimal("20000"), 0L)));
		given(interventionRepository.findProfitRows(any(), any(), isNull(), eq(InterventionStatus.DELIVERED)))
				.willReturn(List.of(new OrderProfitRow(19L, 18L, 1, LocalDateTime.of(2026, 10, 20, 10, 0), "Cliente",
						5L, "Tomás", BigDecimal.ZERO, new BigDecimal("30000"))));

		ProfitReport report = reportService.profit(FROM, TO, null, ADMIN);

		assertThat(report.totals().revenue()).isEqualByComparingTo("705000");
		assertThat(report.totals().partsCost()).isEqualByComparingTo("330000");
		assertThat(report.totals().warrantyCost()).isEqualByComparingTo("30000");
		assertThat(report.totals().profit()).isEqualByComparingTo("375000");
		assertThat(report.byTechnician()).extracting("technicianName").containsExactly("Tomás", "Sin técnico");
		assertThat(report.orders().get(0).code()).isEqualTo("FT-00018-G1");
		assertThat(report.orders().get(0).profit()).isEqualByComparingTo("-30000");
	}

	@Test
	void shouldLimitTechnicianToOwnOrdersEvenIfAnotherIsRequested() {
		given(usageRepository.sumCostByTechnician(any(), any(), eq(5L), any())).willReturn(List.of());
		given(interventionRepository.sumRevenueByTechnician(any(), any(), eq(5L), any())).willReturn(List.of());
		given(interventionRepository.findProfitRows(any(), any(), eq(5L), any())).willReturn(List.of());

		ProfitReport report = reportService.profit(FROM, TO, 7L, TECHNICIAN);

		assertThat(report.technicianId()).isEqualTo(5L);
	}

	@Test
	void shouldListEveryStatusInMaintenanceReport() {
		given(interventionRepository.countByStatusReceivedBetween(any(), any()))
				.willReturn(List.of(new StatusCount(InterventionStatus.DELIVERED, 4L)));
		given(interventionRepository.countByTechnicianReceivedBetween(any(), any(), any(), any(), any(), any()))
				.willReturn(List.of());

		MaintenanceReport report = reportService.maintenance(FROM, TO);

		assertThat(report.byStatus()).hasSize(InterventionStatus.values().length);
		assertThat(report.totalOrders()).isEqualTo(4);
	}

	@Test
	void shouldRejectInvalidRanges() {
		assertThatThrownBy(() -> reportService.maintenance(TO, FROM)).isInstanceOf(BusinessRuleException.class);
		assertThatThrownBy(() -> reportService.maintenance(FROM, FROM.plusDays(400)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("366");
	}
}
