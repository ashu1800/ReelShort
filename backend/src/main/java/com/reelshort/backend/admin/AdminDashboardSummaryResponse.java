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

	/**
	 * 内容指标改用视频库：bookCount/episodeCacheCount 是库存，crawlPage/totalPages 是抓取进度。
	 */
	public record ContentMetrics(long bookCount, long episodeCacheCount, int crawlPage, int totalPages) {
	}

	public record PointMetrics(long totalWatchRewardPoints, int periodDays, String averagePointsPerUserPerDay,
			String averagePointsPerUserPerHour) {
	}

	public record AuditLogMetrics(List<AdminAuditLogResponse> latest) {
	}
}
