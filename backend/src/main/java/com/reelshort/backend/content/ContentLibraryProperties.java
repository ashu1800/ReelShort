package com.reelshort.backend.content;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.content.library")
public class ContentLibraryProperties {

	private boolean enabled = true;

	/** 首页每次随机抽多少部；片库更小就返回全部。 */
	private int sampleLimit = 300;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getSampleLimit() {
		return sampleLimit;
	}

	public void setSampleLimit(int sampleLimit) {
		this.sampleLimit = sampleLimit;
	}
}
