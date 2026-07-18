package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.reelshort.backend.admin.AdminAuditLogRepository;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.withdrawal.TronClient;

@SpringBootTest
class VipOrderAuditTransactionIntegrationTests {

	private static final String WALLET = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE";
	private static final String CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

	@Autowired private VipOrderService vipOrderService;
	@Autowired private VipOrderRepository vipOrders;
	@Autowired private UserAccountRepository users;

	@MockitoBean private TronClient tronClient;
	@MockitoBean private AdminAuditLogRepository auditLogs;

	private UserAccount user;
	private VipOrder order;

	@BeforeEach
	void setUp() {
		user = users.save(UserAccount.create("vip-audit-" + UUID.randomUUID(), "hash", UserStatus.ACTIVE));
		order = vipOrders.save(VipOrder.create(user.id(), "VIP-audit-" + UUID.randomUUID(),
				new BigDecimal("15"), 1, 20, WALLET, CONTRACT));
	}

	@Test
	void manualConfirmationRollsBackOrderAndEntitlementWhenSuccessAuditFails() {
		String txHash = "a".repeat(64);
		TronClient.IncomingTransfer transfer = new TronClient.IncomingTransfer(txHash, order.payableAmount(),
				WALLET, CONTRACT, order.createdAt().plusSeconds(1), 20, true);
		when(tronClient.fetchIncomingUsdtTransfer(txHash, WALLET, CONTRACT, order.payableAmount()))
				.thenReturn(transfer);
		when(tronClient.verifyIncomingTransfer(transfer)).thenReturn(transfer);
		when(auditLogs.save(any())).thenThrow(new IllegalStateException("audit unavailable"));

		assertThatThrownBy(() -> vipOrderService.manualConfirm(order.id(), txHash, "operator"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("audit unavailable");

		assertThat(vipOrders.findById(order.id()).orElseThrow().status()).isEqualTo("PENDING");
		assertThat(users.findById(user.id()).orElseThrow().vipUntil()).isNull();
	}

	@Test
	void rejectionRollsBackOrderWhenSuccessAuditFails() {
		when(auditLogs.save(any())).thenThrow(new IllegalStateException("audit unavailable"));

		assertThatThrownBy(() -> vipOrderService.reject(order.id(), "operator"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("audit unavailable");

		assertThat(vipOrders.findById(order.id()).orElseThrow().status()).isEqualTo("PENDING");
	}
}
