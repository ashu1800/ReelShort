package com.reelshort.backend.admin;

/**
 * 后台「数据统计」响应。since 为 null 表示 all（不限时间窗）；totalPoints 是当前全量账户快照，与时间窗无关。
 */
public record AdminStatisticsResponse(
		String range,
		String since,
		AppMetrics app,
		WatchTaskMetrics watchTask,
		String totalPoints) {

	/** App 自然观看与互动计数。 */
	public record AppMetrics(long plays, long likes, long favorites, long comments) {
	}

	/** 运营观看任务（刷积分）计数、对应领取积分与单次平均积分。 */
	public record WatchTaskMetrics(long plays, long points, String averagePoints) {
	}
}
