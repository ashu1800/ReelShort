package com.reelshort.backend.system.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreTests {

	@Test
	void allowsRequestsUntilLimitIsReached() {
		MutableClock clock = new MutableClock(Instant.parse("2026-06-26T00:00:00Z"));
		InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock);

		RateLimitResult first = store.tryAcquire("login:127.0.0.1", 2, Duration.ofMinutes(1));
		RateLimitResult second = store.tryAcquire("login:127.0.0.1", 2, Duration.ofMinutes(1));

		assertThat(first.allowed()).isTrue();
		assertThat(first.remaining()).isEqualTo(1);
		assertThat(second.allowed()).isTrue();
		assertThat(second.remaining()).isZero();
	}

	@Test
	void rejectsRequestsAfterLimitIsReached() {
		MutableClock clock = new MutableClock(Instant.parse("2026-06-26T00:00:00Z"));
		InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock);

		store.tryAcquire("login:127.0.0.1", 1, Duration.ofMinutes(1));
		RateLimitResult rejected = store.tryAcquire("login:127.0.0.1", 1, Duration.ofMinutes(1));

		assertThat(rejected.allowed()).isFalse();
		assertThat(rejected.remaining()).isZero();
		assertThat(rejected.retryAfterSeconds()).isEqualTo(60);
	}

	@Test
	void startsNewWindowAfterExpiry() {
		MutableClock clock = new MutableClock(Instant.parse("2026-06-26T00:00:00Z"));
		InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock);

		store.tryAcquire("login:127.0.0.1", 1, Duration.ofMinutes(1));
		clock.advance(Duration.ofSeconds(61));
		RateLimitResult allowed = store.tryAcquire("login:127.0.0.1", 1, Duration.ofMinutes(1));

		assertThat(allowed.allowed()).isTrue();
		assertThat(allowed.remaining()).isZero();
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
