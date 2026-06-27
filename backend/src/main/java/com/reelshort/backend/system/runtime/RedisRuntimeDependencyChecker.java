package com.reelshort.backend.system.runtime;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRuntimeDependencyChecker implements RuntimeDependencyChecker {

	private final StringRedisTemplate redisTemplate;

	public RedisRuntimeDependencyChecker(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public RuntimeDependencyStatus check() {
		try {
			String pong = redisTemplate.execute((RedisConnection connection) -> connection.ping());
			return "PONG".equalsIgnoreCase(pong)
					? RuntimeDependencyStatus.up("redis", "pong")
					: RuntimeDependencyStatus.down("redis", "ping failed");
		}
		catch (Exception exception) {
			return RuntimeDependencyStatus.down("redis", "unavailable");
		}
	}
}
