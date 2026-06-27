package com.reelshort.backend.system.logs;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.system.logs")
public record SystemLogProperties(Path root, int maxLines, long maxBytes) {

	private static final long MAX_READ_BYTES = 5_242_880;

	public SystemLogProperties {
		if (root == null) {
			root = Path.of("logs");
		}
		if (maxLines <= 0) {
			maxLines = 500;
		}
		if (maxBytes <= 0) {
			maxBytes = 1_048_576;
		}
		maxBytes = Math.min(maxBytes, MAX_READ_BYTES);
	}

}
