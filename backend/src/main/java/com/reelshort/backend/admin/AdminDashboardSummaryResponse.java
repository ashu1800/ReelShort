package com.reelshort.backend.admin;

import java.util.List;

public record AdminDashboardSummaryResponse(
		UserMetrics users,
		OrderMetrics orders,
		PaymentMetrics payments,
		ContentMetrics content,
		AuditLogMetrics auditLogs) {

	public record UserMetrics(long total, long disabled) {
	}

	public record OrderMetrics(long total, long created, long paid, long totalAmountCents) {
	}

	public record PaymentMetrics(long total, long processed, long rejected) {
	}

	public record ContentMetrics(long bookCount, long episodeCacheCount, long shelfCount) {
	}

	public record AuditLogMetrics(List<AdminAuditLogResponse> latest) {
	}
}
