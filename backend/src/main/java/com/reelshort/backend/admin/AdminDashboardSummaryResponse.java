package com.reelshort.backend.admin;

import java.util.List;

public record AdminDashboardSummaryResponse(
		UserMetrics users,
		VipMetrics vipOrders,
		ContentMetrics content,
		PointMetrics points,
		AuditLogMetrics auditLogs) {

	public record UserMetrics(long total, long disabled) {
	}

	public record VipMetrics(long total, String totalUsdt) {
	}

	public record ContentMetrics(long bookCount, long episodeCacheCount, long shelfCount) {
	}

	public record PointMetrics(long totalWatchRewardPoints, int periodDays, String averagePointsPerUserPerDay,
			String averagePointsPerUserPerHour) {
	}

	public record AuditLogMetrics(List<AdminAuditLogResponse> latest) {
	}
}
