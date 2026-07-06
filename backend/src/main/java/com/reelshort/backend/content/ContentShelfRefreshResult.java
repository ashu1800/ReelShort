package com.reelshort.backend.content;

public record ContentShelfRefreshResult(
		String shelfType,
		String locale,
		String status,
		int itemCount,
		String errorMessage) {

	public static ContentShelfRefreshResult success(ContentShelfType shelfType, ContentLocale locale, int itemCount) {
		return new ContentShelfRefreshResult(shelfType.apiValue(), locale.apiValue(), "SUCCESS", itemCount, null);
	}

	public static ContentShelfRefreshResult failed(ContentShelfType shelfType, ContentLocale locale, String errorMessage) {
		return new ContentShelfRefreshResult(shelfType.apiValue(), locale.apiValue(), "FAILED", 0, errorMessage);
	}
}
