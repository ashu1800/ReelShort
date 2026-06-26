package com.reelshort.backend.content;

import java.util.Arrays;

public enum ContentShelfType {

	RECOMMEND("recommend", "/api/v1/reelshort/recommend"),
	NEW_RELEASE("new-release", "/api/v1/reelshort/newrelease"),
	DRAMA_DUB("drama-dub", "/api/v1/reelshort/dramadub");

	private final String apiValue;
	private final String providerPath;

	ContentShelfType(String apiValue, String providerPath) {
		this.apiValue = apiValue;
		this.providerPath = providerPath;
	}

	public String apiValue() {
		return apiValue;
	}

	String providerPath() {
		return providerPath;
	}

	public static ContentShelfType fromApiValue(String value) {
		return Arrays.stream(values())
				.filter(type -> type.apiValue.equals(value))
				.findFirst()
				.orElseThrow(() -> new ContentProviderException(400, "bad request"));
	}
}
