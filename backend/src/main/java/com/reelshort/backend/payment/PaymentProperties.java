package com.reelshort.backend.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.payment")
public record PaymentProperties(
		String callbackSecret) {

	public PaymentProperties {
		if (callbackSecret == null || callbackSecret.isBlank()) {
			callbackSecret = "dev-payment-callback-secret";
		}
	}
}
