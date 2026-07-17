package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.reelshort.backend.order.VipOrder;
import com.reelshort.backend.order.VipOrderRepository;
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
	private VipOrderRepository vipOrderRepository;

	@Autowired
	private ContentBookCacheRepository contentBookCacheRepository;

	@Autowired
	private ContentEpisodeCacheRepository contentEpisodeCacheRepository;

	@Autowired
	private ContentShelfCacheRepository contentShelfCacheRepository;

	@Autowired
	private AdminAuditService adminAuditService;

	@Test
	void summaryAggregatesUsersVipOrdersContentAndLatestAuditLogs() {
		userAccountRepository.save(UserAccount.create("dashboard-active", "hash", UserStatus.ACTIVE));
		userAccountRepository.save(UserAccount.create("dashboard-disabled", "hash", UserStatus.DISABLED));
		UUID firstVipUserId = UUID.randomUUID();
		UUID secondVipUserId = UUID.randomUUID();
		vipOrderRepository.save(confirmedVipOrder(firstVipUserId, new BigDecimal("9.99")));
		vipOrderRepository.save(confirmedVipOrder(secondVipUserId, new BigDecimal("19.99")));
		contentBookCacheRepository.save(ContentBookCache.from(
				new ContentBook("dashboard-book", "Dashboard", "dashboard", "https://example.com/cover.jpg", "", 2)));
		contentEpisodeCacheRepository.save(ContentEpisodeCache.create("dashboard-book", "dashboard", "[]", 2));
		contentShelfCacheRepository.save(ContentShelfCache.create(ContentShelfType.RECOMMEND, "[]", 1));
		for (int index = 0; index < 6; index++) {
			adminAuditService.record("admin", "DASHBOARD_ACTION_" + index, "DASHBOARD", UUID.randomUUID(),
					"summary " + index);
		}

		AdminDashboardSummaryResponse summary = dashboardService.summary();

		assertThat(summary.users().total()).isGreaterThanOrEqualTo(2);
		assertThat(summary.users().disabled()).isGreaterThanOrEqualTo(1);
		assertThat(summary.vipOrders().total()).isGreaterThanOrEqualTo(2);
		assertThat(new BigDecimal(summary.vipOrders().totalUsdt())).isGreaterThanOrEqualTo(new BigDecimal("30.00"));
		assertThat(summary.content().bookCount()).isGreaterThanOrEqualTo(1);
		assertThat(summary.content().episodeCacheCount()).isGreaterThanOrEqualTo(1);
		assertThat(summary.content().shelfCount()).isGreaterThanOrEqualTo(1);
		assertThat(summary.auditLogs().latest()).hasSize(5);
		assertThat(summary.auditLogs().latest().get(0).action()).isEqualTo("DASHBOARD_ACTION_5");
	}

	private static VipOrder confirmedVipOrder(UUID userId, BigDecimal usdtAmount) {
		VipOrder order = VipOrder.create(userId,
				"VIP" + UUID.randomUUID().toString().replace("-", "").substring(0, 16), usdtAmount, 1, 0,
				"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
		order.confirm("dashboard-tx-" + UUID.randomUUID(), "admin");
		return order;
	}
}

