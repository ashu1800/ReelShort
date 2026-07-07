package com.reelshort.backend.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/app/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ApiResponse<RegisterSimulationResponse> register(@Valid @RequestBody RegisterRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(authService.register(request.countryCode(), request.phoneNumber(),
				request.password(), request.verificationCode()), requestId(httpRequest));
	}

	@PostMapping("/login")
	public ApiResponse<AuthToken> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return ApiResponse.success(authService.login(request.countryCode(), request.phoneNumber(), request.password()),
				requestId(httpRequest));
	}

	@PostMapping("/sms/send")
	public ApiResponse<SmsSendResponse> sendSms(@Valid @RequestBody SmsSendRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(authService.sendSms(request.purpose(), request.countryCode(), request.phoneNumber()),
				requestId(httpRequest));
	}

	@PostMapping("/password/change")
	public ApiResponse<String> changePassword(CurrentUser currentUser,
			@Valid @RequestBody PasswordChangeRequest request,
			HttpServletRequest httpRequest) {
		authService.changePassword(currentUser, request.oldPassword(), request.newPassword(), request.verificationCode());
		return ApiResponse.success("password changed", requestId(httpRequest));
	}

	@PostMapping("/logout")
	public ApiResponse<String> logout(HttpServletRequest httpRequest) {
		authService.logout(extractBearerToken(httpRequest));
		return ApiResponse.success("logged out", requestId(httpRequest));
	}

	private String extractBearerToken(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			throw new AuthException(401, "unauthorized");
		}
		return authorization.substring(7);
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
