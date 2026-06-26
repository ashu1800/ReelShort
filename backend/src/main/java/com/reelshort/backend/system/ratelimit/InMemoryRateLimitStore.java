package com.reelshort.backend.system.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimitStore implements RateLimitStore {

	private final Clock clock;
	private final Map<String, Counter> counters = new HashMap<>();

	public InMemoryRateLimitStore() {
		this(Clock.systemUTC());
	}

	InMemoryRateLimitStore(Clock clock) {
		this.clock = clock;
	}

	@Override
	public synchronized RateLimitResult tryAcquire(String key, int limit, Duration window) {
		Instant now = clock.instant();
		removeExpiredCounters(now);
		Counter counter = counters.get(key);
		if (counter == null || !counter.expiresAt().isAfter(now)) {
			counter = new Counter(0, now.plus(window));
			counters.put(key, counter);
		}
		if (counter.count() >= limit) {
			return RateLimitResult.rejected(limit, retryAfterSeconds(now, counter.expiresAt()));
		}
		counter.increment();
		return RateLimitResult.allowed(limit, limit - counter.count());
	}

	private long retryAfterSeconds(Instant now, Instant expiresAt) {
		return Math.max(1, Duration.between(now, expiresAt).toSeconds());
	}

	private void removeExpiredCounters(Instant now) {
		Iterator<Map.Entry<String, Counter>> iterator = counters.entrySet().iterator();
		while (iterator.hasNext()) {
			if (!iterator.next().getValue().expiresAt().isAfter(now)) {
				iterator.remove();
			}
		}
	}

	private static final class Counter {

		private int count;
		private final Instant expiresAt;

		private Counter(int count, Instant expiresAt) {
			this.count = count;
			this.expiresAt = expiresAt;
		}

		void increment() {
			count++;
		}

		int count() {
			return count;
		}

		Instant expiresAt() {
			return expiresAt;
		}
	}
}
