package com.reelshort.backend.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsCallbackConfiguration {

	@Bean
	SmsCallbackProperties smsCallbackProperties(
			@Value("${reelshort.sms.callback.enabled:true}") boolean enabled,
			@Value("${reelshort.sms.callback.url:}") String url,
			@Value("${reelshort.sms.callback.api-key:}") String apiKey,
			@Value("${reelshort.sms.callback.api-secret:}") String apiSecret,
			@Value("${reelshort.sms.callback.timeout:5s}") Duration timeout) {
		return new SmsCallbackProperties(enabled, url, apiKey, apiSecret, timeout);
	}
}
