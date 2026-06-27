package com.reelshort.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.auth.session")
public class AuthSessionProperties {

	private Duration accessTokenTtl = Duration.ofDays(7);
	private Duration cleanupRetention = Duration.ofDays(1);

	public Duration getAccessTokenTtl() {
		return accessTokenTtl;
	}

	public void setAccessTokenTtl(Duration accessTokenTtl) {
		this.accessTokenTtl = accessTokenTtl;
	}

	public Duration getCleanupRetention() {
		return cleanupRetention;
	}

	public void setCleanupRetention(Duration cleanupRetention) {
		this.cleanupRetention = cleanupRetention;
	}
}
