package com.fixtrack.inventory.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.fixtrack.inventory.entity.SparePart;
import com.fixtrack.inventory.entity.StockStatus;

@ExtendWith(MockitoExtension.class)
class InventoryEventPublisherTest {

	@Mock
	private RealtimePublisher publisher;

	private InventoryEventPublisher events;

	@BeforeEach
	void setUp() {
		events = new InventoryEventPublisher(publisher);
	}

	@Test
	void shouldPublishStockChangeAndLowStockAlertWhenCrossingTheMinimum() {
		SparePart part = part(2, 2);

		events.stockChanged(part, 3);

		verify(publisher).publish(eq(RealtimeTopics.SPARE_PARTS), eq(RealtimeEventType.STOCK_CHANGED), eq("spare-part"),
				eq(4L), anyMap());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
		verify(publisher).publish(eq(RealtimeTopics.STOCK_ALERTS), eq(RealtimeEventType.LOW_STOCK), eq("spare-part"),
				eq(4L), payload.capture());
		assertThat(payload.getValue()).containsEntry("stock", 2).containsEntry("name", "Pin de carga")
				.containsEntry("stockStatus", StockStatus.LOW);
	}

	@Test
	void shouldNotAlertWhenStockStaysAboveTheMinimum() {
		events.stockChanged(part(8, 2), 10);

		verify(publisher, never()).publish(eq(RealtimeTopics.STOCK_ALERTS), any(), anyString(), any(), anyMap());
	}

	@Test
	void shouldAlertOutOfStockAndRestored() {
		assertThat(InventoryEventPublisher.alertFor(StockStatus.LOW, StockStatus.OUT)).isEqualTo(RealtimeEventType.OUT_OF_STOCK);
		assertThat(InventoryEventPublisher.alertFor(StockStatus.OUT, StockStatus.OK)).isEqualTo(RealtimeEventType.STOCK_RESTORED);
		assertThat(InventoryEventPublisher.alertFor(StockStatus.OUT, StockStatus.LOW)).isNull();
		assertThat(InventoryEventPublisher.alertFor(StockStatus.LOW, StockStatus.LOW)).isNull();
	}

	private SparePart part(int stock, int minStock) {
		return SparePart.builder().id(4L).name("Pin de carga").sku("PIN-A54").stock(stock).minStock(minStock).build();
	}
}
