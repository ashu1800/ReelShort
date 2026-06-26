package com.reelshort.backend.system.ratelimit;

import java.time.Duration;

public record RateLimitRule(
		String name,
		String method,
		String pathPattern,
		int limit,
		Duration window) {

	public boolean matches(String requestMethod, String requestPath) {
		if (!method.equalsIgnoreCase(requestMethod)) {
			return false;
		}
		if (pathPattern.endsWith("/**")) {
			String prefix = pathPattern.substring(0, pathPattern.length() - 3);
			return requestPath.equals(prefix) || requestPath.startsWith(prefix + "/");
		}
		return pathPattern.equals(requestPath);
	}
}
