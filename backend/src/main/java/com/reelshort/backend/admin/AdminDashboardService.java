package com.reelshort.backend.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminDashboardSummaryResponse.AuditLogMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.ContentMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.UserMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.VipMetrics;
import com.reelshort.backend.content.ContentBookCacheRepository;
import com.reelshort.backend.content.ContentEpisodeCacheRepository;
import com.reelshort.backend.content.ContentShelfCacheRepository;
import com.reelshort.backend.order.VipOrderRepository;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class AdminDashboardService {

	private final UserAccountRepository userAccountRepository;
	private final VipOrderRepository vipOrderRepository;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ContentEpisodeCacheRepository contentEpisodeCacheRepository;
	private final ContentShelfCacheRepository contentShelfCacheRepository;
	private final AdminAuditLogRepository adminAuditLogRepository;

	public AdminDashboardService(UserAccountRepository userAccountRepository,
			VipOrderRepository vipOrderRepository, ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentShelfCacheRepository contentShelfCacheRepository, AdminAuditLogRepository adminAuditLogRepository) {
		this.userAccountRepository = userAccountRepository;
		this.vipOrderRepository = vipOrderRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.contentEpisodeCacheRepository = contentEpisodeCacheRepository;
		this.contentShelfCacheRepository = contentShelfCacheRepository;
		this.adminAuditLogRepository = adminAuditLogRepository;
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
		AuditLogMetrics auditLogMetrics = new AuditLogMetrics(adminAuditLogRepository.findTop5ByOrderByCreatedAtDesc()
				.stream()
				.map(AdminAuditLogResponse::from)
				.toList());
		return new AdminDashboardSummaryResponse(userMetrics, vipMetrics, contentMetrics, auditLogMetrics);
	}
}
