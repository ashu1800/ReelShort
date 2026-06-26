package com.reelshort.backend.system.api;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
		int code,
		String message,
		T data,
		String requestId,
		String timestamp) {

	public static <T> ApiResponse<T> success(T data, String requestId) {
		return new ApiResponse<>(0, "success", data, requestId, OffsetDateTime.now().toString());
	}
}

