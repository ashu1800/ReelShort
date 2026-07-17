package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.admin.AdminUserStatus;
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.system.security.TotpService;

import jakarta.servlet.http.HttpServletRequest;

class AdminVipOrderControllerTests {

	@Test
	void manualConfirmationRejectsInvalidTotpBeforeChainVerificationAndAuditsFailure() {
		VipOrderService orders = mock(VipOrderService.class);
		AdminUserRepository admins = mock(AdminUserRepository.class);
		TotpService totp = mock(TotpService.class);
		AdminAuditService audit = mock(AdminAuditService.class);
		AdminVipOrderController controller = new AdminVipOrderController(orders, admins, totp, audit);
		AdminUser admin = AdminUser.create("vip-admin", "hash", AdminUserStatus.ACTIVE);
		admin.enableTotp("SECRET");
		CurrentAdmin current = new CurrentAdmin(admin.id(), admin.username(), Set.of("ORDER_WRITE"));
		UUID orderId = UUID.randomUUID();
		when(admins.findById(admin.id())).thenReturn(Optional.of(admin));
		when(totp.verify("SECRET", "123456")).thenReturn(false);

		assertThatThrownBy(() -> controller.confirm(current, orderId,
				new AdminVipOrderController.VipConfirmRequest("a".repeat(64), "123456"), mock(HttpServletRequest.class)))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("2FA");
		verify(orders, never()).manualConfirm(orderId, "a".repeat(64), admin.username());
		verify(audit).recordIndependent(admin.username(), "VIP_ORDER_CONFIRM_FAILED", "VIP_ORDER", orderId,
				"status=TOTP_REJECTED");
	}

	@Test
	void manualConfirmationUsesVerifiedChainServiceAndAuditsSuccess() {
		VipOrderService orders = mock(VipOrderService.class);
		AdminUserRepository admins = mock(AdminUserRepository.class);
		TotpService totp = mock(TotpService.class);
		AdminAuditService audit = mock(AdminAuditService.class);
		AdminVipOrderController controller = new AdminVipOrderController(orders, admins, totp, audit);
		AdminUser admin = AdminUser.create("vip-admin-ok", "hash", AdminUserStatus.ACTIVE);
		admin.enableTotp("SECRET");
		CurrentAdmin current = new CurrentAdmin(admin.id(), admin.username(), Set.of("ORDER_WRITE"));
		UUID orderId = UUID.randomUUID();
		String txHash = "b".repeat(64);
		VipOrder order = VipOrder.create(UUID.randomUUID(), "VIP-manual", new BigDecimal("15"), 1, 20,
				"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
		order.confirm(txHash, admin.username());
		when(admins.findById(admin.id())).thenReturn(Optional.of(admin));
		when(totp.verify("SECRET", "654321")).thenReturn(true);
		when(orders.manualConfirm(orderId, txHash, admin.username())).thenReturn(order);

		controller.confirm(current, orderId,
				new AdminVipOrderController.VipConfirmRequest(txHash, "654321"), mock(HttpServletRequest.class));

		verify(orders).manualConfirm(orderId, txHash, admin.username());
		verify(audit).recordIndependent(admin.username(), "VIP_ORDER_CONFIRM_VERIFIED", "VIP_ORDER", orderId,
				"status=CONFIRMED, confirmedBy=" + admin.username() + ", txHash=" + txHash);
	}
}
