package com.reelshort.backend.system.ratelimit;

import java.time.Duration;

public interface RateLimitStore {

	RateLimitResult tryAcquire(String key, int limit, Duration window);
}
