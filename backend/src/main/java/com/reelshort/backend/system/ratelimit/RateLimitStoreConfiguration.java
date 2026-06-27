package com.reelshort.backend.system.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimitStoreConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "reelshort.rate-limit", name = "store", havingValue = "memory",
			matchIfMissing = true)
	InMemoryRateLimitStore inMemoryRateLimitStore() {
		return new InMemoryRateLimitStore();
	}

	@Bean
	@ConditionalOnProperty(prefix = "reelshort.rate-limit", name = "store", havingValue = "redis")
	RedisRateLimitStore redisRateLimitStore(StringRedisTemplate redisTemplate) {
		return new RedisRateLimitStore(redisTemplate);
	}
}
