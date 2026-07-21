package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;

public interface WithdrawalStatsAggregate {
	BigDecimal getTotalUsdt();
	long getPayoutCount();
}
