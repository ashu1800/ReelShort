package com.reelshort.backend.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.reelshort.backend.system.api.ApiErrorResponse;
import com.reelshort.backend.system.security.CredentialLockedException;

/**
 * P1-5 / P2-17: 异常到 HTTP 状态码的映射。
 */
class GlobalExceptionHandlerTests {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsCredentialLockedExceptionToTooManyRequests() {
		ResponseEntity<ApiErrorResponse> response = handler.handleCredentialLockedException(
				new CredentialLockedException(), request("/api/app/auth/password/change"));

		assertThat(response.getStatusCode().value()).isEqualTo(429);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("too many attempts, try again later");
		assertThat(response.getBody().requestId()).isEqualTo("test-request-id");
	}

	@Test
	void mapsIllegalStateExceptionToConflictWithoutEchoingMessage() {
		ResponseEntity<ApiErrorResponse> response = handler.handleIllegalStateException(
				new IllegalStateException("vip order already expired secret=0xdeadbeef"),
				request("/api/admin/vip/orders/1/confirm"));

		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("conflict");
	}

	@Test
	void mapsDataIntegrityViolationExceptionToBadRequest() {
		ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrityViolationException(
				new DataIntegrityViolationException("value too long for column"), request("/api/app/watch/progress"));

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("bad request");
	}

	private MockHttpServletRequest request(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
		request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-request-id");
		return request;
	}
}
