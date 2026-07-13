package com.reelshort.backend.points;

import java.time.LocalDate;

public record DailyEarningQuotaResponse(
		LocalDate earningDate,
		int fluctuationPercent,
		int effectiveMaximum,
		int earnedPoints,
		int remainingPoints) {

	static DailyEarningQuotaResponse from(DailyEarningQuota quota) {
		return new DailyEarningQuotaResponse(quota.earningDate(), quota.fluctuationPercent(), quota.effectiveMaximum(),
				quota.earnedPoints(), quota.remainingPoints());
	}

	static DailyEarningQuotaResponse unlimited(LocalDate date) {
		return new DailyEarningQuotaResponse(date, 0, 0, 0, -1);
	}
}
