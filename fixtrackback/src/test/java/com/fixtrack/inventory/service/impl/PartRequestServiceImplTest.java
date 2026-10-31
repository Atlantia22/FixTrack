package com.fixtrack.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fixtrack.common.BusinessRuleException;
import com.fixtrack.common.ConflictException;
import com.fixtrack.device.entity.Device;
import com.fixtrack.device.entity.DeviceModel;
import com.fixtrack.infra.security.AuthenticatedUser;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.intervention.entity.InterventionStatus;
import com.fixtrack.intervention.service.InterventionPricingService;
import com.fixtrack.intervention.service.InterventionService;
import com.fixtrack.intervention.service.InterventionSparePartService;
import com.fixtrack.inventory.dto.CreatePartRequestRequest;
import com.fixtrack.inventory.dto.NewSparePartRequest;
import com.fixtrack.inventory.dto.PartReceiptResponse;
import com.fixtrack.inventory.dto.ReceivePartRequestRequest;
import com.fixtrack.inventory.entity.PartQuality;
import com.fixtrack.inventory.entity.PartRequest;
import com.fixtrack.inventory.entity.PartRequestStatus;
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.entity.StockMovement;
import com.fixtrack.inventory.entity.Supplier;
import com.fixtrack.inventory.entity.SupplierSparePart;
import com.fixtrack.inventory.mapper.PartRequestMapper;
import com.fixtrack.inventory.repository.PartRequestRepository;
import com.fixtrack.inventory.repository.SparePartRepository;
import com.fixtrack.inventory.repository.SupplierRepository;
import com.fixtrack.inventory.repository.SupplierSparePartRepository;
import com.fixtrack.inventory.service.SparePartService;
import com.fixtrack.inventory.service.StockService;
import com.fixtrack.person.entity.Person;
import com.fixtrack.person.entity.Role;
import com.fixtrack.person.service.PersonService;

@ExtendWith(MockitoExtension.class)
class PartRequestServiceImplTest {

	private static final AuthenticatedUser TECHNICIAN = new AuthenticatedUser(5L, "Tito", "tito@fixtrack.test",
			Role.TECHNICIAN, "hash", true);
	private static final AuthenticatedUser COORDINATOR = new AuthenticatedUser(2L, "Coord", "coord@fixtrack.test",
			Role.COORDINATOR, "hash", true);

	@Mock
	private PartRequestRepository partRequestRepository;

	@Mock
	private SparePartRepository sparePartRepository;

	@Mock
	private SupplierRepository supplierRepository;

	@Mock
	private SupplierSparePartRepository supplierSparePartRepository;

	@Mock
	private InterventionPricingService interventionLookup;

	@Mock
	private InterventionService interventionService;

	@Mock
	private InterventionSparePartService interventionSparePartService;

	@Mock
	private StockService stockService;

	@Mock
	private SparePartService sparePartService;

	@Mock
	private PersonService personService;

	private PartRequestServiceImpl service;
	private Intervention intervention;
	private SparePart part;

	@BeforeEach
	void setUp() {
		service = new PartRequestServiceImpl(partRequestRepository, sparePartRepository, supplierRepository,
				supplierSparePartRepository, interventionLookup, interventionService, interventionSparePartService,
				stockService, sparePartService, personService, new PartRequestMapper(),
				Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneId.of("America/Bogota")));
		intervention = Intervention.builder().id(18L).status(InterventionStatus.IN_REPAIR)
				.technician(Person.builder().id(5L).fullName("Tito").build()).build();
		part = SparePart.builder().id(4L).name("Pin de carga").sku("PIN-A54").stock(1).build();
	}

	@Test
	void shouldRejectRequestWhenStockIsEnough() {
		given(interventionLookup.getEntity(18L)).willReturn(intervention);
		given(sparePartRepository.findByIdAndActiveTrue(4L)).willReturn(Optional.of(part));

		assertThatThrownBy(() -> service.create(18L, new CreatePartRequestRequest(4L, null, 1, null, null), TECHNICIAN))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("regístrelo como repuesto usado");
		verify(partRequestRepository, never()).save(any());
	}

	@Test
	void shouldRequireCatalogPartOrDescription() {
		given(interventionLookup.getEntity(18L)).willReturn(intervention);

		assertThatThrownBy(() -> service.create(18L, new CreatePartRequestRequest(null, " ", 1, null, null), TECHNICIAN))
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void shouldMoveOrderToWaitingPartsWhenRequesting() {
		Person technician = Person.builder().id(5L).fullName("Tito").build();
		given(interventionLookup.getEntity(18L)).willReturn(intervention);
		given(personService.getEntity(5L)).willReturn(technician);
		given(partRequestRepository.save(any(PartRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

		service.create(18L, new CreatePartRequestRequest(null, "Flex de carga Moto G32", 2, null, null), TECHNICIAN);

		verify(interventionService).markWaitingParts(eq(intervention), eq(technician), anyString());
	}

	@Test
	void shouldAddNewPartToInventoryAndRequestItWhenNotInCatalog() {
		DeviceModel model = DeviceModel.builder().id(29L).name("Galaxy A15").build();
		intervention.setDevice(Device.builder().id(7L).deviceModel(model).build());
		NewSparePartRequest newPart = new NewSparePartRequest(3L, "Flex de botones Galaxy A15", PartQuality.GENERIC,
				new BigDecimal("15000"), new BigDecimal("45000"));
		SparePart created = SparePart.builder().id(40L).name("Flex de botones Galaxy A15").sku("FLE-GALAXYA15-GEN")
				.stock(0).build();
		given(interventionLookup.getEntity(18L)).willReturn(intervention);
		given(sparePartService.createForRequest(newPart, 29L)).willReturn(created);
		given(personService.getEntity(5L)).willReturn(Person.builder().id(5L).fullName("Tito").build());
		given(partRequestRepository.save(any(PartRequest.class))).willAnswer(invocation -> invocation.getArgument(0));

		service.create(18L, new CreatePartRequestRequest(null, null, 1, null, newPart), TECHNICIAN);

		ArgumentCaptor<PartRequest> saved = ArgumentCaptor.forClass(PartRequest.class);
		verify(partRequestRepository).save(saved.capture());
		assertThat(saved.getValue().getSparePart()).isSameAs(created);
		assertThat(saved.getValue().getDescription()).isEqualTo("Flex de botones Galaxy A15");
		assertThat(saved.getValue().getEstimatedCost()).isEqualByComparingTo("15000");
		verify(interventionService).markWaitingParts(eq(intervention), any(Person.class), anyString());
	}

	@Test
	void shouldRejectCatalogPartTogetherWithNewPart() {
		given(interventionLookup.getEntity(18L)).willReturn(intervention);
		NewSparePartRequest newPart = new NewSparePartRequest(3L, "Flex", null, BigDecimal.ONE, BigDecimal.TEN);

		assertThatThrownBy(() -> service.create(18L, new CreatePartRequestRequest(4L, null, 1, null, newPart), TECHNICIAN))
				.isInstanceOf(BusinessRuleException.class);
		verify(sparePartService, never()).createForRequest(any(), any());
	}

	@Test
	void shouldEnterConsumeAndUpdateSupplierCostWhenReceiving() {
		Supplier supplier = Supplier.builder().id(9L).name("Andinos").taxId("900").build();
		PartRequest request = PartRequest.builder().id(3L).intervention(intervention).sparePart(part).supplier(supplier)
				.description("Pin de carga").quantity(2).status(PartRequestStatus.ORDERED).build();
		SupplierSparePart link = new SupplierSparePart(supplier, part);
		given(partRequestRepository.findForUpdate(3L)).willReturn(Optional.of(request));
		given(personService.getEntity(2L)).willReturn(Person.builder().id(2L).fullName("Coord").build());
		given(stockService.registerPurchase(eq(4L), eq(2), any(), anyString(), any(), eq(request)))
				.willReturn(StockMovement.builder().balanceAfter(3).build());
		given(interventionSparePartService.registerReceivedPart(eq(intervention), eq(4L), eq(2), any(), any()))
				.willReturn(true);
		given(supplierSparePartRepository.findById(any())).willReturn(Optional.of(link));
		given(supplierSparePartRepository.findBySparePartIdInAndPreferredTrue(List.of(4L))).willReturn(List.of());

		PartReceiptResponse receipt = service.receive(3L, new ReceivePartRequestRequest(new BigDecimal("21000"), null),
				COORDINATOR);

		assertThat(receipt.consumedInOrder()).isTrue();
		assertThat(receipt.stockAfter()).isEqualTo(1);
		assertThat(receipt.request().status()).isEqualTo(PartRequestStatus.RECEIVED);
		assertThat(link.getLastCost()).isEqualByComparingTo("21000");
	}

	@Test
	void shouldRequireCatalogPartBeforeReceiving() {
		PartRequest request = PartRequest.builder().id(3L).intervention(intervention).description("Flex a mano")
				.quantity(1).status(PartRequestStatus.ORDERED).build();
		given(partRequestRepository.findForUpdate(3L)).willReturn(Optional.of(request));

		assertThatThrownBy(() -> service.receive(3L, new ReceivePartRequestRequest(BigDecimal.TEN, null), COORDINATOR))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Asocie el repuesto");
		verify(stockService, never()).registerPurchase(any(), any(Integer.class), any(), any(), any(), any());
	}

	@Test
	void shouldCancelOnlyRequestsNotYetOrdered() {
		PartRequest pending = PartRequest.builder().id(1L).status(PartRequestStatus.PENDING).build();
		PartRequest approved = PartRequest.builder().id(2L).status(PartRequestStatus.APPROVED).build();
		given(partRequestRepository.findByInterventionIdAndStatusIn(eq(18L), any())).willReturn(List.of(pending, approved));

		int cancelled = service.cancelNotOrdered(18L);

		assertThat(cancelled).isEqualTo(2);
		assertThat(pending.getStatus()).isEqualTo(PartRequestStatus.CANCELLED);
		assertThat(approved.getStatus()).isEqualTo(PartRequestStatus.CANCELLED);
		verify(partRequestRepository).findByInterventionIdAndStatusIn(18L,
				java.util.EnumSet.of(PartRequestStatus.PENDING, PartRequestStatus.APPROVED));
	}
}
