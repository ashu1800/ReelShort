package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.reelshort.backend.content.ContentBookCacheRepository;
import com.reelshort.backend.content.ContentEpisodeCacheRepository;
import com.reelshort.backend.content.ContentShelfCacheRepository;
import com.reelshort.backend.order.VipOrderRepository;
import com.reelshort.backend.points.PointTransactionRepository;
import com.reelshort.backend.user.UserAccountRepository;

class AdminDashboardPointMetricsTests {

	@Test
	void averagesWatchRewardsAcrossAllRegisteredUsers() {
		UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
		VipOrderRepository vipOrderRepository = mock(VipOrderRepository.class);
		ContentBookCacheRepository contentBookCacheRepository = mock(ContentBookCacheRepository.class);
		ContentEpisodeCacheRepository contentEpisodeCacheRepository = mock(ContentEpisodeCacheRepository.class);
		ContentShelfCacheRepository contentShelfCacheRepository = mock(ContentShelfCacheRepository.class);
		AdminAuditLogRepository adminAuditLogRepository = mock(AdminAuditLogRepository.class);
		PointTransactionRepository pointTransactionRepository = mock(PointTransactionRepository.class);
		when(userAccountRepository.count()).thenReturn(4L);
		when(vipOrderRepository.sumConfirmedPayableUsdtAmount()).thenReturn(BigDecimal.ZERO);
		when(adminAuditLogRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());
		when(pointTransactionRepository.sumWatchRewardAmountByCreatedAtBetween(any(), any())).thenReturn(1_200L);
		Clock clock = Clock.fixed(Instant.parse("2026-11-06T05:00:00Z"), ZoneOffset.UTC);
		AdminDashboardService service = new AdminDashboardService(userAccountRepository, vipOrderRepository,
				contentBookCacheRepository, contentEpisodeCacheRepository, contentShelfCacheRepository,
				adminAuditLogRepository, pointTransactionRepository, clock);

		var metrics = service.summary().points();

		assertThat(metrics.totalWatchRewardPoints()).isEqualTo(1_200L);
		assertThat(metrics.periodDays()).isEqualTo(30);
		assertThat(metrics.averagePointsPerUserPerDay()).isEqualTo("10");
		assertThat(metrics.averagePointsPerUserPerHour()).isEqualTo("0.4167");
		ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
		ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(pointTransactionRepository).sumWatchRewardAmountByCreatedAtBetween(startCaptor.capture(),
				endCaptor.capture());
		assertThat(startCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-10-08T00:00:00+08:00"));
		assertThat(endCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2026-11-07T00:00:00+08:00"));
	}

	@Test
	void avoidsDivisionByZeroWhenThereAreNoUsers() {
		UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
		VipOrderRepository vipOrderRepository = mock(VipOrderRepository.class);
		ContentBookCacheRepository contentBookCacheRepository = mock(ContentBookCacheRepository.class);
		ContentEpisodeCacheRepository contentEpisodeCacheRepository = mock(ContentEpisodeCacheRepository.class);
		ContentShelfCacheRepository contentShelfCacheRepository = mock(ContentShelfCacheRepository.class);
		AdminAuditLogRepository adminAuditLogRepository = mock(AdminAuditLogRepository.class);
		PointTransactionRepository pointTransactionRepository = mock(PointTransactionRepository.class);
		when(userAccountRepository.count()).thenReturn(0L);
		when(vipOrderRepository.sumConfirmedPayableUsdtAmount()).thenReturn(BigDecimal.ZERO);
		when(adminAuditLogRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());
		when(pointTransactionRepository.sumWatchRewardAmountByCreatedAtBetween(any(), any())).thenReturn(0L);
		AdminDashboardService service = new AdminDashboardService(userAccountRepository, vipOrderRepository,
				contentBookCacheRepository, contentEpisodeCacheRepository, contentShelfCacheRepository,
				adminAuditLogRepository, pointTransactionRepository,
				Clock.fixed(Instant.parse("2026-11-06T05:00:00Z"), ZoneOffset.UTC));

		var metrics = service.summary().points();

		assertThat(metrics.averagePointsPerUserPerDay()).isEqualTo("0");
		assertThat(metrics.averagePointsPerUserPerHour()).isEqualTo("0");
	}
}
