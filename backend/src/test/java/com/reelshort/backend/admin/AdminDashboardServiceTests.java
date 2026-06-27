package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.content.ContentBook;
import com.reelshort.backend.content.ContentBookCache;
import com.reelshort.backend.content.ContentBookCacheRepository;
import com.reelshort.backend.content.ContentEpisodeCache;
import com.reelshort.backend.content.ContentEpisodeCacheRepository;
import com.reelshort.backend.content.ContentShelfCache;
import com.reelshort.backend.content.ContentShelfCacheRepository;
import com.reelshort.backend.content.ContentShelfType;
import com.reelshort.backend.order.CreateRechargeOrderRequest;
import com.reelshort.backend.order.RechargeOrderService;
import com.reelshort.backend.payment.PaymentCallbackRequest;
import com.reelshort.backend.payment.PaymentCallbackService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@SpringBootTest
class AdminDashboardServiceTests {

	@Autowired
	private AdminDashboardService dashboardService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private RechargeOrderService rechargeOrderService;

	@Autowired
	private PaymentCallbackService paymentCallbackService;

	@Autowired
	private ContentBookCacheRepository contentBookCacheRepository;

	@Autowired
	private ContentEpisodeCacheRepository contentEpisodeCacheRepository;

	@Autowired
	private ContentShelfCacheRepository contentShelfCacheRepository;

	@Autowired
	private AdminAuditService adminAuditService;

	@Test
	void summaryAggregatesUsersOrdersPaymentsContentAndLatestAuditLogs() {
		userAccountRepository.save(UserAccount.create("dashboard-active", "hash", UserStatus.ACTIVE));
		userAccountRepository.save(UserAccount.create("dashboard-disabled", "hash", UserStatus.DISABLED));
		UUID paidUserId = UUID.randomUUID();
		UUID createdUserId = UUID.randomUUID();
		var paidOrder = rechargeOrderService.create(paidUserId, new CreateRechargeOrderRequest(990, 99));
		rechargeOrderService.create(createdUserId, new CreateRechargeOrderRequest(1990, 199));
		paymentCallbackService.handle(new PaymentCallbackRequest("dashboard-paid-event", paidOrder.orderNo(),
				"mock-pay", 990));
		assertThatThrownBy(() -> paymentCallbackService.handle(new PaymentCallbackRequest("dashboard-rejected-event",
				"missing-order", "mock-pay", 990)));
		contentBookCacheRepository.save(ContentBookCache.from(
				new ContentBook("dashboard-book", "Dashboard", "dashboard", "https://example.com/cover.jpg", 2)));
		contentEpisodeCacheRepository.save(ContentEpisodeCache.create("dashboard-book", "dashboard", "[]", 2));
		contentShelfCacheRepository.save(ContentShelfCache.create(ContentShelfType.RECOMMEND, "[]", 1));
		for (int index = 0; index < 6; index++) {
			adminAuditService.record("admin", "DASHBOARD_ACTION_" + index, "DASHBOARD", UUID.randomUUID(),
					"summary " + index);
		}

		AdminDashboardSummaryResponse summary = dashboardService.summary();

		assertThat(summary.users().total()).isGreaterThanOrEqualTo(2);
		assertThat(summary.users().disabled()).isGreaterThanOrEqualTo(1);
		assertThat(summary.orders().total()).isGreaterThanOrEqualTo(2);
		assertThat(summary.orders().created()).isGreaterThanOrEqualTo(1);
		assertThat(summary.orders().paid()).isGreaterThanOrEqualTo(1);
		assertThat(summary.orders().totalAmountCents()).isGreaterThanOrEqualTo(2980);
		assertThat(summary.payments().total()).isGreaterThanOrEqualTo(2);
		assertThat(summary.payments().processed()).isGreaterThanOrEqualTo(1);
		assertThat(summary.payments().rejected()).isGreaterThanOrEqualTo(1);
		assertThat(summary.content().bookCount()).isGreaterThanOrEqualTo(1);
		assertThat(summary.content().episodeCacheCount()).isGreaterThanOrEqualTo(1);
		assertThat(summary.content().shelfCount()).isGreaterThanOrEqualTo(1);
		assertThat(summary.auditLogs().latest()).hasSize(5);
		assertThat(summary.auditLogs().latest().get(0).action()).isEqualTo("DASHBOARD_ACTION_5");
	}
}
