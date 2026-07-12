package com.reelshort.backend.admin;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.admin")
public record AdminProperties(
		String username,
		String passwordHash,
		Duration tokenTtl) {

	public AdminProperties {
		if (username == null || username.isBlank()) {
			username = "admin";
		}
		if (passwordHash == null || passwordHash.isBlank()) {
			passwordHash = "";
		}
		if (tokenTtl == null) {
			tokenTtl = Duration.ofHours(8);
		}
	}
}
