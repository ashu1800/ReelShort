package com.reelshort.backend.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminDashboardSummaryResponse.AuditLogMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.ContentMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.PointMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.UserMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.VipMetrics;
import com.reelshort.backend.content.ContentBookCacheRepository;
import com.reelshort.backend.content.ContentEpisodeCacheRepository;
import com.reelshort.backend.content.ContentShelfCacheRepository;
import com.reelshort.backend.order.VipOrderRepository;
import com.reelshort.backend.points.PointTransactionRepository;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class AdminDashboardService {

	private static final ZoneId POINTS_ZONE = ZoneId.of("Asia/Shanghai");
	private static final int POINTS_PERIOD_DAYS = 30;

	private final UserAccountRepository userAccountRepository;
	private final VipOrderRepository vipOrderRepository;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ContentEpisodeCacheRepository contentEpisodeCacheRepository;
	private final ContentShelfCacheRepository contentShelfCacheRepository;
	private final AdminAuditLogRepository adminAuditLogRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final Clock clock;

	public AdminDashboardService(UserAccountRepository userAccountRepository,
			VipOrderRepository vipOrderRepository, ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentShelfCacheRepository contentShelfCacheRepository, AdminAuditLogRepository adminAuditLogRepository,
			PointTransactionRepository pointTransactionRepository, Clock clock) {
		this.userAccountRepository = userAccountRepository;
		this.vipOrderRepository = vipOrderRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.contentEpisodeCacheRepository = contentEpisodeCacheRepository;
		this.contentShelfCacheRepository = contentShelfCacheRepository;
		this.adminAuditLogRepository = adminAuditLogRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AdminDashboardSummaryResponse summary() {
		UserMetrics userMetrics = new UserMetrics(
				userAccountRepository.count(),
				userAccountRepository.countByStatus(UserStatus.DISABLED));
		long totalVip = vipOrderRepository.countByStatus("CONFIRMED");
		String totalUsdt = vipOrderRepository.sumConfirmedPayableUsdtAmount().stripTrailingZeros().toPlainString();
		VipMetrics vipMetrics = new VipMetrics(totalVip, totalUsdt);
		ContentMetrics contentMetrics = new ContentMetrics(
				contentBookCacheRepository.count(),
				contentEpisodeCacheRepository.count(),
				contentShelfCacheRepository.count());
		PointMetrics pointMetrics = pointMetrics(userMetrics.total());
		AuditLogMetrics auditLogMetrics = new AuditLogMetrics(adminAuditLogRepository.findTop5ByOrderByCreatedAtDesc()
				.stream()
				.map(AdminAuditLogResponse::from)
				.toList());
		return new AdminDashboardSummaryResponse(userMetrics, vipMetrics, contentMetrics, pointMetrics, auditLogMetrics);
	}

	/**
	 * 近 30 天观看奖励按全部注册用户摊平：每日、每小时的用户平均获取量。
	 * 只统计用户观看产生的 WATCH_REWARD，不把后台手动调整等非自然获取计入。
	 */
	private PointMetrics pointMetrics(long totalUsers) {
		LocalDate today = LocalDate.now(clock.withZone(POINTS_ZONE));
		OffsetDateTime startInclusive = today.minusDays(POINTS_PERIOD_DAYS - 1L)
				.atStartOfDay(POINTS_ZONE).toOffsetDateTime();
		OffsetDateTime endExclusive = today.plusDays(1).atStartOfDay(POINTS_ZONE).toOffsetDateTime();
		long totalWatchRewardPoints = pointTransactionRepository.sumWatchRewardAmountByCreatedAtBetween(startInclusive,
				endExclusive);
		if (totalUsers <= 0) {
			return new PointMetrics(totalWatchRewardPoints, POINTS_PERIOD_DAYS, "0", "0");
		}
		BigDecimal points = BigDecimal.valueOf(totalWatchRewardPoints);
		BigDecimal userDays = BigDecimal.valueOf(totalUsers).multiply(BigDecimal.valueOf(POINTS_PERIOD_DAYS));
		BigDecimal averagePerDay = points.divide(userDays, 2, RoundingMode.HALF_UP);
		BigDecimal averagePerHour = points.divide(userDays.multiply(BigDecimal.valueOf(24)), 4,
				RoundingMode.HALF_UP);
		return new PointMetrics(totalWatchRewardPoints, POINTS_PERIOD_DAYS, plain(averagePerDay), plain(averagePerHour));
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}
