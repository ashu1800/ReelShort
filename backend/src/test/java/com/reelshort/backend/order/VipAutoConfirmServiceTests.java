package com.reelshort.backend.order;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.reelshort.backend.withdrawal.TronClient;
import com.reelshort.backend.withdrawal.TronProperties;

class VipAutoConfirmServiceTests {

	private static final String ADDRESS = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE";
	private static final String CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

	@Test
	void expiresOrdersBeforeCheckingWhetherAnyCanBePolled() {
		VipOrderService orders = mock(VipOrderService.class);
		TronClient tron = mock(TronClient.class);
		VipAutoConfirmService service = new VipAutoConfirmService(orders, tron, new TronProperties());
		when(orders.pendingOrders()).thenReturn(List.of());

		service.autoConfirmPendingOrders();

		InOrder sequence = inOrder(orders);
		sequence.verify(orders).expireOverdueOrders();
		sequence.verify(orders).pendingOrders();
	}

	@Test
	void confirmsOnlyTransferMatchingImmutableOrderSnapshot() {
		VipOrderService orders = mock(VipOrderService.class);
		TronClient tron = mock(TronClient.class);
		TronProperties properties = new TronProperties();
		VipAutoConfirmService service = new VipAutoConfirmService(orders, tron, properties);
		VipOrder order = VipOrder.create(UUID.randomUUID(), "VIP-auto", new BigDecimal("15"), 1, 20,
				ADDRESS, CONTRACT);
		TronClient.IncomingTransfer transfer = new TronClient.IncomingTransfer("b".repeat(64),
				order.payableAmount(), ADDRESS, CONTRACT, order.createdAt().plusSeconds(5),
				properties.getRequiredConfirmations(), true);
		when(orders.pendingOrders()).thenReturn(List.of(order));
		when(tron.fetchIncomingUsdtTransfers(ADDRESS, 200, order.createdAt())).thenReturn(List.of(transfer));
		when(tron.verifyIncomingTransfer(transfer)).thenReturn(transfer);

		service.autoConfirmPendingOrders();

		verify(orders).autoConfirm(order.id(), transfer);
	}
}
