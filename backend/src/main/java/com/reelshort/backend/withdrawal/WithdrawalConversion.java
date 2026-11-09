package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 提现换算与手续费规则。
 *
 * <p>手续费按**到账 USDT** 收取，不从积分里多扣：用户提交的积分全部按比例换成 USDT
 * （{@link #usdtGross(int)}），再从这个金额里扣掉 {@code feePercent}，剩下的才是实际打款额
 * （{@link #usdtAmount(int)}）。例如 10% 手续费下，折算 10.00 USDT 的积分实际到账 9.00 USDT，
 * 扣减的积分始终等于用户输入的积分。
 *
 * <p>最低提现门槛作用在折算总额上（用户"提现 10 USDT"到账 9 USDT），所以
 * {@link #minimumPoints()} 是最小的、折算总额能达到 {@code minimumUsdt} 的积分数。
 */
final class WithdrawalConversion {

	private static final BigDecimal POINTS_PER_RATE_UNIT = BigDecimal.valueOf(50);
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final int USDT_SCALE = 2;

	private final BigDecimal usdtPer50Points;
	private final BigDecimal minimumUsdt;
	private final int feePercent;

	WithdrawalConversion(BigDecimal usdtPer50Points, BigDecimal minimumUsdt, int feePercent) {
		if (usdtPer50Points.signum() <= 0 || minimumUsdt.signum() <= 0 || feePercent < 0 || feePercent >= 100) {
			throw new IllegalArgumentException("withdrawal conversion values must be positive");
		}
		this.usdtPer50Points = usdtPer50Points;
		this.minimumUsdt = minimumUsdt;
		this.feePercent = feePercent;
	}

	int minimumPoints() {
		long low = 1;
		long high = Integer.MAX_VALUE;
		if (usdtGross((int) high).compareTo(minimumUsdt) < 0) {
			throw new IllegalArgumentException("minimum withdrawal points overflow");
		}
		while (low < high) {
			long middle = low + (high - low) / 2;
			if (usdtGross((int) middle).compareTo(minimumUsdt) >= 0) {
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

	/** 折算总额：全部提交积分按比例换成的 USDT，向下截断到分。 */
	BigDecimal usdtGross(int pointAmount) {
		requirePositive(pointAmount);
		return usdtPer50Points.multiply(BigDecimal.valueOf(pointAmount))
				.divide(POINTS_PER_RATE_UNIT, USDT_SCALE, RoundingMode.DOWN);
	}

	/**
	 * 手续费：从折算总额中扣除的 USDT，等于 {@code 总额 - 到账额}，因此两者相加永远等于总额。
	 */
	BigDecimal usdtFee(int pointAmount) {
		return usdtGross(pointAmount).subtract(usdtAmount(pointAmount));
	}

	/** 实际打款额：折算总额扣除手续费后向下截断到分，不会四舍五入。 */
	BigDecimal usdtAmount(int pointAmount) {
		BigDecimal gross = usdtGross(pointAmount);
		if (feePercent == 0) {
			return gross;
		}
		return gross.multiply(BigDecimal.valueOf(100L - feePercent))
				.divide(HUNDRED, USDT_SCALE, RoundingMode.DOWN);
	}

	BigDecimal usdtPer50Points() {
		return usdtPer50Points;
	}

	BigDecimal minimumUsdt() {
		return minimumUsdt;
	}

	private static void requirePositive(int pointAmount) {
		if (pointAmount <= 0) {
			throw new IllegalArgumentException("point amount must be positive");
		}
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
		return new Snapshot(minimumPoints(), strip(usdtPer50Points), strip(usdtPerPoint()), strip(minimumUsdt),
				feePercent);
	}

	private static String strip(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}
