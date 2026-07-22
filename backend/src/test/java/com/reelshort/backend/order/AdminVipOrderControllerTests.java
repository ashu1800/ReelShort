package com.reelshort.backend.order;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.CurrentAdmin;

import jakarta.servlet.http.HttpServletRequest;

class AdminVipOrderControllerTests {

	@Test
	void manualConfirmationDelegatesWithTxHashWithoutSecondFactor() {
		VipOrderService orders = mock(VipOrderService.class);
		AdminAuditService audit = mock(AdminAuditService.class);
		AdminVipOrderController controller = new AdminVipOrderController(orders, audit);
		CurrentAdmin current = new CurrentAdmin(UUID.randomUUID(), "vip-admin", Set.of("ORDER_WRITE"));
		UUID orderId = UUID.randomUUID();
		String txHash = "b".repeat(64);
		VipOrder order = VipOrder.create(UUID.randomUUID(), "VIP-manual", new BigDecimal("15"), 1, 20,
				"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
		order.confirm(txHash, current.username());
		when(orders.manualConfirm(orderId, txHash, current.username())).thenReturn(order);

		controller.confirm(current, orderId, new AdminVipOrderController.VipConfirmRequest(txHash),
				mock(HttpServletRequest.class));

		verify(orders).manualConfirm(orderId, txHash, current.username());
		verifyNoInteractions(audit);
	}
}
