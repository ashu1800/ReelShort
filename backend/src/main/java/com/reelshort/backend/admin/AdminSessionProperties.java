package com.reelshort.backend.admin;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.admin.session")
public class AdminSessionProperties {

	private Duration cleanupRetention = Duration.ofDays(1);

	public Duration getCleanupRetention() {
		return cleanupRetention;
	}

	public void setCleanupRetention(Duration cleanupRetention) {
		this.cleanupRetention = cleanupRetention;
	}
}
