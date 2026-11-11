package com.reelshort.backend.admin;

import java.util.Locale;

/**
 * 后台「数据统计」的时间窗。对外参数值固定为 today / 7d / 30d / all，解析时大小写不敏感。
 */
public enum AdminStatisticsRange {

	TODAY("today"),
	SEVEN_DAYS("7d"),
	THIRTY_DAYS("30d"),
	ALL("all");

	private final String value;

	AdminStatisticsRange(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	/** 非法参数抛 400，与其它后台接口的参数校验风格保持一致。 */
	public static AdminStatisticsRange from(String raw) {
		String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		for (AdminStatisticsRange range : values()) {
			if (range.value.equals(normalized)) {
				return range;
			}
		}
		throw new AdminException(400, "unsupported statistics range");
	}
}
