package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class WithdrawalStatsServiceTests {

	@Test
	void todayUsesShanghaiCalendarAndApprovedErc20Aggregate() {
		WithdrawalRequestRepository repository = mock(WithdrawalRequestRepository.class);
		WithdrawalStatsAggregate aggregate = mock(WithdrawalStatsAggregate.class);
		when(repository.aggregateApprovedErc20(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(aggregate);
		when(aggregate.getTotalUsdt()).thenReturn(new BigDecimal("12.500000"));
		when(aggregate.getPayoutCount()).thenReturn(2L);

		WithdrawalStatsResponse response = new WithdrawalStatsService(repository,
				Clock.fixed(Instant.parse("2026-07-21T03:00:00Z"), ZoneOffset.UTC))
				.stats(WithdrawalStatsRange.TODAY);

		assertThat(response.range()).isEqualTo(WithdrawalStatsRange.TODAY);
		assertThat(response.from()).isEqualTo("2026-07-21T00:00:00+08:00");
		assertThat(response.to()).isEqualTo("2026-07-22T00:00:00+08:00");
		assertThat(response.totalUsdt()).isEqualTo("12.5");
		assertThat(response.payoutCount()).isEqualTo(2);
	}

	@Test
	void lastMonthUsesPreviousMonthBoundaries() {
		WithdrawalRequestRepository repository = mock(WithdrawalRequestRepository.class);
		when(repository.aggregateApprovedErc20(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(null);

		WithdrawalStatsResponse response = new WithdrawalStatsService(repository,
				Clock.fixed(Instant.parse("2026-07-21T03:00:00Z"), ZoneOffset.UTC))
				.stats(WithdrawalStatsRange.LAST_MONTH);

		assertThat(response.from()).isEqualTo("2026-06-01T00:00:00+08:00");
		assertThat(response.to()).isEqualTo("2026-07-01T00:00:00+08:00");
		assertThat(response.totalUsdt()).isEqualTo("0");
		assertThat(response.payoutCount()).isZero();
	}
}
