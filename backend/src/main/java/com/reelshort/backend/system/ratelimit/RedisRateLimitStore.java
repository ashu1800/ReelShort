package com.reelshort.backend.system.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisRateLimitStore implements RateLimitStore {

	private static final String KEY_PREFIX = "reelshort:rate-limit:";

	private final StringRedisTemplate redisTemplate;

	public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public RateLimitResult tryAcquire(String key, int limit, Duration window) {
		String redisKey = KEY_PREFIX + key;
		Long count = redisTemplate.opsForValue().increment(redisKey);
		if (count == null) {
			count = 1L;
		}
		if (count == 1L) {
			redisTemplate.expire(redisKey, window);
			return RateLimitResult.allowed(limit, Math.max(0, limit - 1));
		}
		if (count > limit) {
			return RateLimitResult.rejected(limit, retryAfterSeconds(redisKey, window));
		}
		repairMissingTtl(redisKey, window);
		return RateLimitResult.allowed(limit, Math.max(0, limit - count.intValue()));
	}

	private long retryAfterSeconds(String redisKey, Duration window) {
		Long ttlSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
		if (ttlSeconds == null || ttlSeconds < 1) {
			redisTemplate.expire(redisKey, window);
			return 1;
		}
		return ttlSeconds;
	}

	private void repairMissingTtl(String redisKey, Duration window) {
		Long ttlSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
		if (ttlSeconds == null || ttlSeconds < 1) {
			redisTemplate.expire(redisKey, window);
		}
	}
}
