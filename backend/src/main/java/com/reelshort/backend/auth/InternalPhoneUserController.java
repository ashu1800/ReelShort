package com.reelshort.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/internal/users")
public class InternalPhoneUserController {

	private final AuthService authService;
	private final String superToken;

	public InternalPhoneUserController(AuthService authService,
			@Value("${reelshort.internal.super-token:}") String superToken) {
		this.authService = authService;
		this.superToken = superToken;
	}

	@PostMapping("/register-phone")
	public ApiResponse<AuthToken> registerPhone(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			@Valid @RequestBody InternalPhoneRegisterRequest request,
			HttpServletRequest httpRequest) {
		if (providedToken == null || providedToken.isBlank()) {
			throw new AuthException(401, "unauthorized");
		}
		if (superToken.isBlank() || !superToken.equals(providedToken)) {
			throw new AuthException(403, "forbidden");
		}
		return ApiResponse.success(authService.internalRegisterPhone(request.countryCode(), request.phoneNumber(),
				request.password()), requestId(httpRequest));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
