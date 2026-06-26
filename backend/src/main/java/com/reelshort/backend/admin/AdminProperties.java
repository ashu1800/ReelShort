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
			passwordHash = "$2b$12$Z6hLISkw3ha14uQQ8PKANun8vjeUlMA4U8S841Sz5vfrhmQRhr6wm";
		}
		if (tokenTtl == null) {
			tokenTtl = Duration.ofHours(8);
		}
	}
}
