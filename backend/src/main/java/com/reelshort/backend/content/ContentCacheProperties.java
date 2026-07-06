package com.reelshort.backend.content;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.content.cache")
public class ContentCacheProperties {

	private Duration videoFallbackTtl = Duration.ofMinutes(10);

	public Duration getVideoFallbackTtl() {
		return videoFallbackTtl;
	}

	public void setVideoFallbackTtl(Duration videoFallbackTtl) {
		this.videoFallbackTtl = videoFallbackTtl;
	}
}
