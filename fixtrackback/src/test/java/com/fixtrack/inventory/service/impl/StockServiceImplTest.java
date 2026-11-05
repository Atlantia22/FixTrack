package com.fixtrack.inventory.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fixtrack.common.ConflictException;
import com.fixtrack.intervention.entity.Intervention;
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.entity.StockMovement;
import com.fixtrack.inventory.events.InventoryEventPublisher;
import com.fixtrack.inventory.repository.SparePartRepository;
import com.fixtrack.inventory.repository.StockMovementRepository;
import com.fixtrack.person.entity.Person;

@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

	@Mock
	private SparePartRepository sparePartRepository;

	@Mock
	private StockMovementRepository stockMovementRepository;

	@Mock
	private InventoryEventPublisher inventoryEvents;

	@InjectMocks
	private StockServiceImpl stockService;

	private final Person technician = Person.builder().id(5L).fullName("Tito").build();
	private final Intervention order = Intervention.builder().id(18L).build();

	@Test
	void shouldPublishTheNewStockWithThePreviousBalanceWhenUsedInAnOrder() {
		SparePart part = SparePart.builder().id(4L).name("Pin").sku("PIN-A54").stock(3).minStock(2)
				.costPrice(new BigDecimal("10000")).build();
		given(sparePartRepository.findByIdAndActiveTrue(4L)).willReturn(Optional.of(part));
		given(sparePartRepository.saveAndFlush(part)).willReturn(part);
		given(stockMovementRepository.save(any(StockMovement.class))).willAnswer(invocation -> invocation.getArgument(0));

		stockService.consumeForRepair(4L, 1, order, technician);

		verify(inventoryEvents).stockChanged(part, 3);
	}

	@Test
	void shouldNotPublishWhenStockIsNotEnough() {
		SparePart part = SparePart.builder().id(4L).name("Pin").stock(1).minStock(2).build();
		given(sparePartRepository.findByIdAndActiveTrue(4L)).willReturn(Optional.of(part));

		assertThatThrownBy(() -> stockService.consumeForRepair(4L, 2, order, technician))
				.isInstanceOf(ConflictException.class);
		verify(inventoryEvents, never()).stockChanged(any(), anyInt());
	}
}
