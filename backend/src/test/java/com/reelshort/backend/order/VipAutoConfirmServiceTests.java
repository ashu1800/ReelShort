package com.reelshort.backend.order;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
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
		VipTransferScanCursorService cursors = mock(VipTransferScanCursorService.class);
		VipAutoConfirmService service = new VipAutoConfirmService(orders, tron, new TronProperties(), cursors);
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
		properties.setUsdtContract("TJRabPrwbZy45sbavfcjinPJC18kjpRTv8");
		VipTransferScanCursorService cursors = mock(VipTransferScanCursorService.class);
		VipAutoConfirmService service = new VipAutoConfirmService(orders, tron, properties, cursors);
		VipOrder order = VipOrder.create(UUID.randomUUID(), "VIP-auto", new BigDecimal("15"), 1, 20,
				ADDRESS, CONTRACT);
		TronClient.IncomingTransfer transfer = new TronClient.IncomingTransfer("b".repeat(64),
				order.payableAmount(), ADDRESS, CONTRACT, order.createdAt().plusSeconds(5),
				properties.getRequiredConfirmations(), true);
		when(orders.pendingOrders()).thenReturn(List.of(order));
		UUID cursorId = UUID.randomUUID();
		when(cursors.start(ADDRESS, CONTRACT, order.createdAt()))
				.thenReturn(new VipTransferScanCursorService.State(cursorId, null));
		when(tron.fetchIncomingUsdtTransferPage(ADDRESS, CONTRACT, 200, null))
				.thenReturn(new TronClient.IncomingTransferPage(List.of(transfer), null));
		when(tron.verifyIncomingTransfer(transfer)).thenReturn(transfer);

		service.autoConfirmPendingOrders();

		verify(orders).autoConfirm(order.id(), transfer);
		verify(cursors).complete(cursorId);
	}

	@Test
	void durableCursorReachesThirdPageAcrossThreePollingRounds() {
		VipOrderService orders = mock(VipOrderService.class);
		TronClient tron = mock(TronClient.class);
		TronProperties properties = new TronProperties();
		properties.setIncomingTransferMaxPages(1);
		VipTransferScanCursorService cursors = mock(VipTransferScanCursorService.class);
		VipAutoConfirmService service = new VipAutoConfirmService(orders, tron, properties, cursors);
		VipOrder order = VipOrder.create(UUID.randomUUID(), "VIP-third-page", new BigDecimal("15"), 1, 20,
				ADDRESS, CONTRACT);
		TronClient.IncomingTransfer transfer = new TronClient.IncomingTransfer("c".repeat(64),
				order.payableAmount(), ADDRESS, CONTRACT, order.createdAt().plusSeconds(5),
				properties.getRequiredConfirmations(), true);
		UUID cursorId = UUID.randomUUID();
		when(orders.pendingOrders()).thenReturn(List.of(order));
		when(cursors.start(ADDRESS, CONTRACT, order.createdAt()))
				.thenReturn(new VipTransferScanCursorService.State(cursorId, null),
						new VipTransferScanCursorService.State(cursorId, "page-2"),
						new VipTransferScanCursorService.State(cursorId, "page-3"));
		when(tron.fetchIncomingUsdtTransferPage(ADDRESS, CONTRACT, 200, null))
				.thenReturn(new TronClient.IncomingTransferPage(List.of(), "page-2"));
		when(tron.fetchIncomingUsdtTransferPage(ADDRESS, CONTRACT, 200, "page-2"))
				.thenReturn(new TronClient.IncomingTransferPage(List.of(), "page-3"));
		when(tron.fetchIncomingUsdtTransferPage(ADDRESS, CONTRACT, 200, "page-3"))
				.thenReturn(new TronClient.IncomingTransferPage(List.of(transfer), null));
		when(tron.verifyIncomingTransfer(transfer)).thenReturn(transfer);

		service.autoConfirmPendingOrders();
		verify(orders, never()).autoConfirm(order.id(), transfer);
		service.autoConfirmPendingOrders();
		verify(orders, never()).autoConfirm(order.id(), transfer);
		service.autoConfirmPendingOrders();

		verify(cursors).advance(cursorId, "page-2");
		verify(cursors).advance(cursorId, "page-3");
		verify(cursors).complete(cursorId);
		verify(orders).autoConfirm(order.id(), transfer);
	}

	@Test
	void immatureExactCandidateKeepsCursorOnPageUntilReceiptConfirms() {
		VipOrderService orders = mock(VipOrderService.class);
		TronClient tron = mock(TronClient.class);
		TronProperties properties = new TronProperties();
		properties.setIncomingTransferMaxPages(1);
		VipTransferScanCursorService cursors = mock(VipTransferScanCursorService.class);
		VipAutoConfirmService service = new VipAutoConfirmService(orders, tron, properties, cursors);
		VipOrder order = VipOrder.create(UUID.randomUUID(), "VIP-immature", new BigDecimal("15"), 1, 20,
				ADDRESS, CONTRACT);
		TronClient.IncomingTransfer raw = new TronClient.IncomingTransfer("d".repeat(64), order.payableAmount(),
				ADDRESS, CONTRACT, order.createdAt().plusSeconds(5), 0, true);
		TronClient.IncomingTransfer immature = new TronClient.IncomingTransfer(raw.txHash(), raw.amount(), ADDRESS,
				CONTRACT, raw.blockTimestamp(), 0, false);
		TronClient.IncomingTransfer mature = new TronClient.IncomingTransfer(raw.txHash(), raw.amount(), ADDRESS,
				CONTRACT, raw.blockTimestamp(), properties.getRequiredConfirmations(), true);
		UUID cursorId = UUID.randomUUID();
		when(orders.pendingOrders()).thenReturn(List.of(order));
		when(cursors.start(ADDRESS, CONTRACT, order.createdAt()))
				.thenReturn(new VipTransferScanCursorService.State(cursorId, null));
		when(tron.fetchIncomingUsdtTransferPage(ADDRESS, CONTRACT, 200, null))
				.thenReturn(new TronClient.IncomingTransferPage(List.of(raw), "page-2"));
		when(tron.verifyIncomingTransfer(raw)).thenReturn(immature, mature);

		service.autoConfirmPendingOrders();

		verify(cursors, never()).advance(cursorId, "page-2");
		verify(orders, never()).autoConfirm(order.id(), mature);

		service.autoConfirmPendingOrders();

		verify(orders).autoConfirm(order.id(), mature);
		verify(cursors).advance(cursorId, "page-2");
	}

	@Test
	void terminallyFailedExactCandidateDoesNotBlockFollowingPage() {
		VipOrderService orders = mock(VipOrderService.class);
		TronClient tron = mock(TronClient.class);
		TronProperties properties = new TronProperties();
		properties.setIncomingTransferMaxPages(1);
		VipTransferScanCursorService cursors = mock(VipTransferScanCursorService.class);
		VipAutoConfirmService service = new VipAutoConfirmService(orders, tron, properties, cursors);
		VipOrder order = VipOrder.create(UUID.randomUUID(), "VIP-failed", new BigDecimal("15"), 1, 20,
				ADDRESS, CONTRACT);
		TronClient.IncomingTransfer raw = new TronClient.IncomingTransfer("e".repeat(64), order.payableAmount(),
				ADDRESS, CONTRACT, order.createdAt().plusSeconds(5), 0, true);
		TronClient.IncomingTransfer failed = new TronClient.IncomingTransfer(raw.txHash(), raw.amount(), ADDRESS,
				CONTRACT, raw.blockTimestamp(), 0, false, TronClient.ReceiptVerification.FAILED);
		UUID cursorId = UUID.randomUUID();
		when(orders.pendingOrders()).thenReturn(List.of(order));
		when(cursors.start(ADDRESS, CONTRACT, order.createdAt()))
				.thenReturn(new VipTransferScanCursorService.State(cursorId, null));
		when(tron.fetchIncomingUsdtTransferPage(ADDRESS, CONTRACT, 200, null))
				.thenReturn(new TronClient.IncomingTransferPage(List.of(raw), "page-2"));
		when(tron.verifyIncomingTransfer(raw)).thenReturn(failed);

		service.autoConfirmPendingOrders();

		verify(cursors).advance(cursorId, "page-2");
		verify(orders, never()).autoConfirm(order.id(), failed);
		verify(orders).recordTerminalReceiptFailure(order.id(), failed.txHash());
	}
}
