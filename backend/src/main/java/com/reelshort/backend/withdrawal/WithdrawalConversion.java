package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class WithdrawalConversion {

	private static final BigDecimal POINTS_PER_RATE_UNIT = BigDecimal.valueOf(50);
	private static final BigDecimal MINIMUM_USDT = new BigDecimal("0.01");
	private static final int USDT_SCALE = 2;

	private final BigDecimal usdtPer50Points;
	private final int feePercent;

	WithdrawalConversion(BigDecimal usdtPer50Points, int feePercent) {
		if (usdtPer50Points.signum() <= 0 || feePercent < 0 || feePercent >= 100) {
			throw new IllegalArgumentException("withdrawal conversion values must be positive");
		}
		this.usdtPer50Points = usdtPer50Points;
		this.feePercent = feePercent;
	}

	int minimumPoints() {
		long low = 1;
		long high = Integer.MAX_VALUE;
		if (usdtAmount((int) high).compareTo(MINIMUM_USDT) < 0) {
			throw new IllegalArgumentException("minimum withdrawal points overflow");
		}
		while (low < high) {
			long middle = low + (high - low) / 2;
			if (usdtAmount((int) middle).compareTo(MINIMUM_USDT) >= 0) {
				high = middle;
			}
			else {
				low = middle + 1;
			}
		}
		return (int) low;
	}

	BigDecimal usdtPerPoint() {
		return usdtPer50Points.divide(POINTS_PER_RATE_UNIT);
	}

	BigDecimal usdtAmount(int pointAmount) {
		if (pointAmount <= 0) {
			throw new IllegalArgumentException("point amount must be positive");
		}
		return usdtPer50Points.multiply(BigDecimal.valueOf(withdrawablePoints(pointAmount)))
				.divide(POINTS_PER_RATE_UNIT, USDT_SCALE, RoundingMode.DOWN);
	}

	int feeAmount(int pointAmount) {
		if (pointAmount <= 0) {
			throw new IllegalArgumentException("point amount must be positive");
		}
		long rawFee = (long) pointAmount * feePercent;
		return (int) ((rawFee + 99) / 100);
	}

	int withdrawablePoints(int pointAmount) {
		return pointAmount - feeAmount(pointAmount);
	}

	BigDecimal usdtPer50Points() {
		return usdtPer50Points;
	}

	BigDecimal minimumUsdt() {
		return MINIMUM_USDT;
	}

	/**
	 * Snapshot of the withdrawal conversion thresholds, exposing the same computed values the App
	 * sees in {@link WithdrawalSummaryResponse} but without any user-specific fields. Used by the
	 * operations API for scripts/tools that need the current limits.
	 */
	public record Snapshot(
			int minimumPoints,
			String usdtPer50Points,
			String usdtPerPoint,
			String minimumUsdt,
			int feePercent) {
	}

	Snapshot toSnapshot(int feePercent) {
		return new Snapshot(minimumPoints(), strip(usdtPer50Points), strip(usdtPerPoint()), strip(MINIMUM_USDT),
				feePercent);
	}

	private static String strip(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}
