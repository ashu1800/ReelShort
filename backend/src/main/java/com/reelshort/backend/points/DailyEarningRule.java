package com.reelshort.backend.points;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "point_daily_earning_rules")
class DailyEarningRule {

	@Id
	@Column(name = "earning_date")
	private LocalDate earningDate;

	@Column(name = "base_maximum", nullable = false)
	private int baseMaximum;

	@Column(name = "fluctuation_maximum_percent", nullable = false)
	private int fluctuationMaximumPercent;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected DailyEarningRule() {
	}

	private DailyEarningRule(LocalDate earningDate, int baseMaximum, int fluctuationMaximumPercent,
			OffsetDateTime createdAt) {
		this.earningDate = earningDate;
		this.baseMaximum = baseMaximum;
		this.fluctuationMaximumPercent = fluctuationMaximumPercent;
		this.createdAt = createdAt;
	}

	static DailyEarningRule create(LocalDate date, int baseMaximum, int fluctuationMaximumPercent,
			OffsetDateTime createdAt) {
		return new DailyEarningRule(date, baseMaximum, fluctuationMaximumPercent, createdAt);
	}

	LocalDate earningDate() {
		return earningDate;
	}

	int baseMaximum() {
		return baseMaximum;
	}

	int fluctuationMaximumPercent() {
		return fluctuationMaximumPercent;
	}
}
