package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class WithdrawalConversion {

	private static final int USDT_SCALE = 6;

	private final BigDecimal cnyPerPoint;
	private final BigDecimal cnyPerUsd;
	private final BigDecimal minimumUsd;

	WithdrawalConversion(BigDecimal cnyPerPoint, BigDecimal cnyPerUsd, BigDecimal minimumUsd) {
		if (cnyPerPoint.signum() <= 0 || cnyPerUsd.signum() <= 0 || minimumUsd.signum() <= 0) {
			throw new IllegalArgumentException("withdrawal conversion values must be positive");
		}
		this.cnyPerPoint = cnyPerPoint;
		this.cnyPerUsd = cnyPerUsd;
		this.minimumUsd = minimumUsd;
	}

	int minimumPoints() {
		BigDecimal points = minimumUsd.multiply(cnyPerUsd).divide(cnyPerPoint, 0, RoundingMode.CEILING);
		if (points.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
			throw new IllegalArgumentException("minimum withdrawal points overflow");
		}
		return points.intValueExact();
	}

	int minimumPointsScaled(boolean fairMode) {
		return fairMode ? minimumPoints() * 10 : minimumPoints();
	}

	BigDecimal usdtPerPoint() {
		return cnyPerPoint.divide(cnyPerUsd, 8, RoundingMode.HALF_UP);
	}

	BigDecimal usdtAmount(int points) {
		if (points <= 0) {
			throw new IllegalArgumentException("point amount must be positive");
		}
		return cnyPerPoint.multiply(BigDecimal.valueOf(points)).divide(cnyPerUsd, USDT_SCALE, RoundingMode.HALF_UP);
	}

	BigDecimal cnyPerPoint() {
		return cnyPerPoint;
	}

	BigDecimal cnyPerUsd() {
		return cnyPerUsd;
	}

	BigDecimal minimumUsd() {
		return minimumUsd;
	}

	/**
	 * Snapshot of the withdrawal conversion thresholds, exposing the same computed values the App
	 * sees in {@link WithdrawalSummaryResponse} but without any user-specific fields. Used by the
	 * operations API for scripts/tools that need the current limits.
	 */
	public record Snapshot(
			int minimumPoints,
			String usdtPerPoint,
			String cnyPerPoint,
			String cnyPerUsd,
			String minimumUsd) {
	}

	Snapshot toSnapshot() {
		return new Snapshot(minimumPoints(), strip(usdtPerPoint()), strip(cnyPerPoint), strip(cnyPerUsd),
				strip(minimumUsd));
	}

	private static String strip(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}
