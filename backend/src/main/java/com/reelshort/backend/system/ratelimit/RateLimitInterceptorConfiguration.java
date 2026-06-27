package com.reelshort.backend.system.ratelimit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RateLimitInterceptorConfiguration {

	@Bean
	RateLimitInterceptor rateLimitInterceptor(RateLimitProperties properties, RateLimitStore rateLimitStore,
			RateLimitKeyResolver rateLimitKeyResolver, ObjectMapper objectMapper) {
		return new RateLimitInterceptor(properties, rateLimitStore, rateLimitKeyResolver, objectMapper);
	}
}
