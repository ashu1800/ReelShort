package com.reelshort.backend.system.api;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
		int code,
		String message,
		String path,
		String requestId,
		String timestamp) {

	public static ApiErrorResponse of(int code, String message, String path, String requestId) {
		return new ApiErrorResponse(code, message, path, requestId, OffsetDateTime.now().toString());
	}
}

