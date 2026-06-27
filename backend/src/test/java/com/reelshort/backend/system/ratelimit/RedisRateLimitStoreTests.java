package com.reelshort.backend.system.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRateLimitStoreTests {

	private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
	@SuppressWarnings("unchecked")
	private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
	private final RedisRateLimitStore store = new RedisRateLimitStore(redisTemplate);

	@Test
	void allowsRequestsAndSetsExpiryWhenWindowStarts() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("reelshort:rate-limit:app-auth:ip:203.0.113.10")).thenReturn(1L);

		RateLimitResult result = store.tryAcquire("app-auth:ip:203.0.113.10", 2, Duration.ofMinutes(1));

		assertThat(result.allowed()).isTrue();
		assertThat(result.remaining()).isEqualTo(1);
		verify(redisTemplate).expire("reelshort:rate-limit:app-auth:ip:203.0.113.10", Duration.ofMinutes(1));
	}

	@Test
	void rejectsRequestsAfterLimitIsReached() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("reelshort:rate-limit:app-auth:ip:203.0.113.10")).thenReturn(3L);
		when(redisTemplate.getExpire("reelshort:rate-limit:app-auth:ip:203.0.113.10", TimeUnit.SECONDS))
				.thenReturn(42L);

		RateLimitResult result = store.tryAcquire("app-auth:ip:203.0.113.10", 2, Duration.ofMinutes(1));

		assertThat(result.allowed()).isFalse();
		assertThat(result.remaining()).isZero();
		assertThat(result.retryAfterSeconds()).isEqualTo(42);
	}

	@Test
	void repairsMissingExpiryOnExistingCounter() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("reelshort:rate-limit:app-auth:ip:203.0.113.10")).thenReturn(2L);
		when(redisTemplate.getExpire("reelshort:rate-limit:app-auth:ip:203.0.113.10", TimeUnit.SECONDS))
				.thenReturn(-1L);

		RateLimitResult result = store.tryAcquire("app-auth:ip:203.0.113.10", 3, Duration.ofMinutes(1));

		assertThat(result.allowed()).isTrue();
		assertThat(result.remaining()).isEqualTo(1);
		verify(redisTemplate).expire("reelshort:rate-limit:app-auth:ip:203.0.113.10", Duration.ofMinutes(1));
	}

	@Test
	void usesOneSecondRetryWhenRedisTtlIsMissingForRejectedCounter() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.increment("reelshort:rate-limit:app-auth:ip:203.0.113.10")).thenReturn(4L);
		when(redisTemplate.getExpire("reelshort:rate-limit:app-auth:ip:203.0.113.10", TimeUnit.SECONDS))
				.thenReturn(-1L);

		RateLimitResult result = store.tryAcquire("app-auth:ip:203.0.113.10", 2, Duration.ofMinutes(1));

		assertThat(result.allowed()).isFalse();
		assertThat(result.retryAfterSeconds()).isEqualTo(1);
		verify(redisTemplate).expire("reelshort:rate-limit:app-auth:ip:203.0.113.10", Duration.ofMinutes(1));
	}
}
