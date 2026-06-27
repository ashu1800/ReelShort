package com.reelshort.backend.system.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class RateLimitStoreConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(RateLimitStoreConfiguration.class,
					RateLimitInterceptorConfiguration.class))
			.withBean(RateLimitProperties.class)
			.withBean(RateLimitKeyResolver.class)
			.withBean(ObjectMapper.class);

	@Test
	void usesMemoryStoreByDefault() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(RateLimitStore.class);
			assertThat(context).hasSingleBean(InMemoryRateLimitStore.class);
			assertThat(context).hasSingleBean(RateLimitInterceptor.class);
			assertThat(context).doesNotHaveBean(RedisRateLimitStore.class);
		});
	}

	@Test
	void usesRedisStoreWhenConfigured() {
		contextRunner
				.withBean(StringRedisTemplate.class, () -> org.mockito.Mockito.mock(StringRedisTemplate.class))
				.withPropertyValues("reelshort.rate-limit.store=redis")
				.run(context -> {
					assertThat(context).hasSingleBean(RateLimitStore.class);
					assertThat(context).hasSingleBean(RedisRateLimitStore.class);
					assertThat(context).hasSingleBean(RateLimitInterceptor.class);
					assertThat(context).doesNotHaveBean(InMemoryRateLimitStore.class);
				});
	}
}
