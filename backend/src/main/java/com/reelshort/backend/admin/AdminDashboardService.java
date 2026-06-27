package com.reelshort.backend.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminDashboardSummaryResponse.AuditLogMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.ContentMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.OrderMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.PaymentMetrics;
import com.reelshort.backend.admin.AdminDashboardSummaryResponse.UserMetrics;
import com.reelshort.backend.content.ContentBookCacheRepository;
import com.reelshort.backend.content.ContentEpisodeCacheRepository;
import com.reelshort.backend.content.ContentShelfCacheRepository;
import com.reelshort.backend.order.RechargeOrderRepository;
import com.reelshort.backend.order.RechargeOrderStatus;
import com.reelshort.backend.payment.PaymentEventRepository;
import com.reelshort.backend.payment.PaymentEventStatus;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class AdminDashboardService {

	private final UserAccountRepository userAccountRepository;
	private final RechargeOrderRepository rechargeOrderRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ContentEpisodeCacheRepository contentEpisodeCacheRepository;
	private final ContentShelfCacheRepository contentShelfCacheRepository;
	private final AdminAuditLogRepository adminAuditLogRepository;

	public AdminDashboardService(UserAccountRepository userAccountRepository,
			RechargeOrderRepository rechargeOrderRepository, PaymentEventRepository paymentEventRepository,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentShelfCacheRepository contentShelfCacheRepository, AdminAuditLogRepository adminAuditLogRepository) {
		this.userAccountRepository = userAccountRepository;
		this.rechargeOrderRepository = rechargeOrderRepository;
		this.paymentEventRepository = paymentEventRepository;
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
		OrderMetrics orderMetrics = new OrderMetrics(
				rechargeOrderRepository.count(),
				rechargeOrderRepository.countByStatus(RechargeOrderStatus.CREATED),
				rechargeOrderRepository.countByStatus(RechargeOrderStatus.PAID),
				rechargeOrderRepository.sumAmountCents());
		PaymentMetrics paymentMetrics = new PaymentMetrics(
				paymentEventRepository.count(),
				paymentEventRepository.countByStatus(PaymentEventStatus.PROCESSED),
				paymentEventRepository.countByStatus(PaymentEventStatus.REJECTED));
		ContentMetrics contentMetrics = new ContentMetrics(
				contentBookCacheRepository.count(),
				contentEpisodeCacheRepository.count(),
				contentShelfCacheRepository.count());
		AuditLogMetrics auditLogMetrics = new AuditLogMetrics(adminAuditLogRepository.findTop5ByOrderByCreatedAtDesc()
				.stream()
				.map(AdminAuditLogResponse::from)
				.toList());
		return new AdminDashboardSummaryResponse(userMetrics, orderMetrics, paymentMetrics, contentMetrics,
				auditLogMetrics);
	}
}
