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
		this.createdAt = now;
		this.updatedAt = now;
	}

	static DailyEarningQuota create(UUID userId, DailyEarningRule rule, int fluctuationPercent, OffsetDateTime now) {
		return new DailyEarningQuota(UUID.randomUUID(), userId, rule.earningDate(), fluctuationPercent,
				WatchRewardCalculation.effectiveDailyLimit(rule.baseMaximum(), fluctuationPercent), now);
	}

	int allocate(int requestedPoints, OffsetDateTime now) {
		int granted = Math.min(Math.max(0, requestedPoints), remainingPoints());
		this.earnedPoints += granted;
		this.updatedAt = now;
		return granted;
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

	LocalDate earningDate() {
		return earningDate;
	}
}
