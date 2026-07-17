package com.reelshort.backend.points;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "point_daily_earning_quotas", uniqueConstraints = {
		@UniqueConstraint(name = "uk_point_daily_earning_quota_user_date", columnNames = { "user_id", "earning_date" })
})
class DailyEarningQuota {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "earning_date", nullable = false)
	private LocalDate earningDate;

	@Column(name = "fluctuation_percent", nullable = false)
	private int fluctuationPercent;

	@Column(name = "effective_maximum", nullable = false)
	private int effectiveMaximum;

	@Column(name = "earned_points", nullable = false)
	private int earnedPoints;

	// 十分位小数部分（0-9）。earnedPoints 存真实整数，fractionalEarned 累积观看奖励的小数尾数。
	@Column(name = "fractional_earned", nullable = false)
	private int fractionalEarned;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected DailyEarningQuota() {
	}

	private DailyEarningQuota(UUID id, UUID userId, LocalDate earningDate, int fluctuationPercent,
			int effectiveMaximum, OffsetDateTime now) {
		this.id = id;
		this.userId = userId;
		this.earningDate = earningDate;
		this.fluctuationPercent = fluctuationPercent;
		this.effectiveMaximum = effectiveMaximum;
		this.earnedPoints = 0;
		this.fractionalEarned = 0;
		this.createdAt = now;
		this.updatedAt = now;
	}

	static DailyEarningQuota create(UUID userId, DailyEarningRule rule, int fluctuationPercent, OffsetDateTime now) {
		return new DailyEarningQuota(UUID.randomUUID(), userId, rule.earningDate(), fluctuationPercent,
				WatchRewardCalculation.effectiveDailyLimit(rule.baseMaximum(), fluctuationPercent), now);
	}

	/**
	 * 以"十分位"为单位分配每日奖励配额，自动处理小数进位。
	 * 返回实际分配的十分位数（例如返回 13 表示分配了 1.3 分）。
	 */
	int allocateTenths(int requestedTenths, OffsetDateTime now) {
		int maxTenths = effectiveMaximum * 10;
		long currentTenths = (long) this.earnedPoints * 10 + this.fractionalEarned;
		long remaining = maxTenths - currentTenths;
		if (remaining <= 0 || requestedTenths <= 0) {
			return 0;
		}
		long grantedTenths = Math.min((long) requestedTenths, remaining);
		long newTotal = currentTenths + grantedTenths;
		this.earnedPoints = (int) (newTotal / 10);
		this.fractionalEarned = (int) (newTotal % 10);
		this.updatedAt = now;
		return (int) grantedTenths;
	}

	int remainingPoints() {
		return Math.max(0, effectiveMaximum - earnedPoints);
	}

	int fluctuationPercent() {
		return fluctuationPercent;
	}

	int effectiveMaximum() {
		return effectiveMaximum;
	}

	int earnedPoints() {
		return earnedPoints;
	}

	int fractionalEarned() {
		return fractionalEarned;
	}

	LocalDate earningDate() {
		return earningDate;
	}
}
