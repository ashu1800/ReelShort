package com.reelshort.backend.auth;

import java.time.Duration;

public record SmsCallbackProperties(
		boolean enabled,
		String url,
		String apiKey,
		String apiSecret,
		Duration timeout) {
}
