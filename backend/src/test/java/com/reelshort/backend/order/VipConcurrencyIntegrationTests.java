package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@SpringBootTest
class VipConcurrencyIntegrationTests {
	@Autowired private VipOrderService orders;
	@Autowired private VipEntitlementService entitlements;
	@Autowired private UserAccountRepository users;
	@Autowired private SystemConfigService configs;

	@Test
	void concurrentOrderCreationUsesDistinctPayableSlotsAcrossIndependentTransactions() throws Exception {
		configs.update(SystemConfigRegistry.VIP_COLLECTION_ADDRESS, "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE");
		UserAccount first = users.save(UserAccount.create("vip-concurrent-a-" + UUID.randomUUID(), "hash",
				UserStatus.ACTIVE));
		UserAccount second = users.save(UserAccount.create("vip-concurrent-b-" + UUID.randomUUID(), "hash",
				UserStatus.ACTIVE));
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var firstFuture = executor.submit(() -> { start.await(); return orders.create(first.id()); });
			var secondFuture = executor.submit(() -> { start.await(); return orders.create(second.id()); });
			start.countDown();
			VipOrder firstOrder = firstFuture.get();
			VipOrder secondOrder = secondFuture.get();

			assertThat(firstOrder.id()).isNotEqualTo(secondOrder.id());
			assertThat(firstOrder.payableAmount()).isNotEqualByComparingTo(secondOrder.payableAmount());
			orders.reject(firstOrder.id(), "test-cleanup");
			orders.reject(secondOrder.id(), "test-cleanup");
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentVipGrantsAccumulateTwoMonthsForSameUser() throws Exception {
		UserAccount user = users.save(UserAccount.create("vip-grant-" + UUID.randomUUID(), "hash", UserStatus.ACTIVE));
		OffsetDateTime before = OffsetDateTime.now();
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> { start.await(); entitlements.grantMonth(user.id()); return null; });
			var second = executor.submit(() -> { start.await(); entitlements.grantMonth(user.id()); return null; });
			start.countDown();
			first.get();
			second.get();
		}
		finally {
			executor.shutdownNow();
		}

		OffsetDateTime vipUntil = users.findById(user.id()).orElseThrow().vipUntil();
		assertThat(vipUntil).isAfter(before.plusDays(59));
		assertThat(vipUntil).isBefore(before.plusDays(61));
	}
}
