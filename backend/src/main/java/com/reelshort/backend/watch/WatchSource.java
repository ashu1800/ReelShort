package com.reelshort.backend.watch;

/**
 * 观看记录来源：APP 为用户在客户端自然观看，OPERATIONS 为内部运营模拟观看任务（刷积分）。
 * 统计口径据此区分 App 播放量与运营任务播放量；V37 之前的历史行统一按 APP 处理。
 */
public enum WatchSource {
	APP,
	OPERATIONS
}
