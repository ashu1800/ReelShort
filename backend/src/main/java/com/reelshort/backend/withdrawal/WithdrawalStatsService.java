package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class WithdrawalStatsService {

	private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
	private static final DateTimeFormatter RESPONSE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	private final WithdrawalRequestRepository repository;
	private final Clock clock;

	@Autowired
	public WithdrawalStatsService(WithdrawalRequestRepository repository) {
		this(repository, Clock.system(ZONE));
	}

	WithdrawalStatsService(WithdrawalRequestRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public WithdrawalStatsResponse stats(WithdrawalStatsRange range) {
		LocalDate today = LocalDate.now(clock.withZone(ZONE));
		LocalDate startDate;
		LocalDate endDate;
		switch (range) {
			case TODAY -> {
				startDate = today;
				endDate = today.plusDays(1);
			}
			case YESTERDAY -> {
				startDate = today.minusDays(1);
				endDate = today;
			}
			case THIS_WEEK -> {
				startDate = today.with(DayOfWeek.MONDAY);
				endDate = startDate.plusWeeks(1);
			}
			case THIS_MONTH -> {
				startDate = today.withDayOfMonth(1);
				endDate = startDate.plusMonths(1);
			}
			case LAST_MONTH -> {
				endDate = today.withDayOfMonth(1);
				startDate = endDate.minusMonths(1);
			}
			default -> throw new IllegalArgumentException("unsupported withdrawal stats range");
		}
		OffsetDateTime from = startDate.atStartOfDay(ZONE).toOffsetDateTime();
		OffsetDateTime to = endDate.atStartOfDay(ZONE).toOffsetDateTime();
		WithdrawalStatsAggregate aggregate = repository.aggregateApprovedErc20(from, to);
		BigDecimal total = aggregate == null || aggregate.getTotalUsdt() == null
				? BigDecimal.ZERO : aggregate.getTotalUsdt();
		long count = aggregate == null ? 0L : aggregate.getPayoutCount();
		return new WithdrawalStatsResponse(range, RESPONSE_TIMESTAMP.format(from), RESPONSE_TIMESTAMP.format(to),
				total.stripTrailingZeros().toPlainString(), count);
	}
}
