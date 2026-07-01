package com.reelshort.backend.content;

import java.util.Arrays;

public enum ContentLocale {
	ENGLISH("en"),
	TRADITIONAL_CHINESE("zh-TW");

	private final String apiValue;

	ContentLocale(String apiValue) {
		this.apiValue = apiValue;
	}

	public String apiValue() {
		return apiValue;
	}

	public static ContentLocale fromApiValue(String value) {
		if (value == null || value.isBlank()) {
			return ENGLISH;
		}
		return Arrays.stream(values())
				.filter(locale -> locale.apiValue.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unsupported locale: " + value));
	}
}
