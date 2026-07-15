package com.reelshort.backend.content;

public enum ContentLocale {
	ENGLISH("en");

	private final String apiValue;

	ContentLocale(String apiValue) {
		this.apiValue = apiValue;
	}

	public String apiValue() {
		return apiValue;
	}

	public static ContentLocale fromApiValue(String value) {
		// The app is English-only; any incoming locale resolves to ENGLISH.
		return ENGLISH;
	}
}
