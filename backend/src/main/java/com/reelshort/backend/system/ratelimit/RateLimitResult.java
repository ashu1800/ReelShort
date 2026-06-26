package com.reelshort.backend.system.ratelimit;

public record RateLimitResult(
		boolean allowed,
		int limit,
		int remaining,
		long retryAfterSeconds) {

	public static RateLimitResult allowed(int limit, int remaining) {
		return new RateLimitResult(true, limit, remaining, 0);
	}

	public static RateLimitResult rejected(int limit, long retryAfterSeconds) {
		return new RateLimitResult(false, limit, 0, retryAfterSeconds);
	}
}
