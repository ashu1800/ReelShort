package com.reelshort.backend.auth;

import java.util.List;
import java.util.UUID;

public record InternalPhoneBatchRegisterResponse(
		int total,
		int successCount,
		int failureCount,
		List<Result> results) {

	public static InternalPhoneBatchRegisterResponse from(List<Result> results) {
		int successCount = (int) results.stream().filter(Result::success).count();
		return new InternalPhoneBatchRegisterResponse(
				results.size(),
				successCount,
				results.size() - successCount,
				List.copyOf(results));
	}

	public record Result(
			int index,
			boolean success,
			String countryCode,
			String phoneNumber,
			UUID userId,
			String username,
			String phoneE164,
			String token,
			String tokenType,
			String errorCode,
			String message) {

		public static Result success(int index, InternalPhoneRegisterRequest request, AuthToken token) {
			return new Result(index, true, request.countryCode(), request.phoneNumber(), token.userId(),
					token.username(), token.phoneE164(), token.token(), token.tokenType(), null, null);
		}

		public static Result failure(int index, InternalPhoneRegisterRequest request, String errorCode, String message) {
			return new Result(index, false, request.countryCode(), request.phoneNumber(), null, null, null, null, null,
					errorCode, message);
		}
	}
}
