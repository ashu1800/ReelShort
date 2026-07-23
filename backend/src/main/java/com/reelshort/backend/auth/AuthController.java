package com.reelshort.backend.auth;

import org.springframework.web.bind.annotation.GetMapping;
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
	private final CaptchaService captchaService;

	public AuthController(AuthService authService, CaptchaService captchaService) {
		this.authService = authService;
		this.captchaService = captchaService;
	}

	@PostMapping("/register")
	public ApiResponse<AuthToken> register(@Valid @RequestBody RegisterRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(authService.register(request.username(), request.password(), request.captchaId(),
				request.captchaAnswer()), requestId(httpRequest));
	}

	@PostMapping("/login")
	public ApiResponse<AuthToken> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return ApiResponse.success(authService.login(request.username(), request.password(), request.loginSource()),
				requestId(httpRequest));
	}

	@GetMapping("/captcha")
	public ApiResponse<CaptchaResponse> captcha(HttpServletRequest httpRequest) {
		CaptchaChallenge challenge = captchaService.generate();
		return ApiResponse.success(new CaptchaResponse(challenge.id().toString(), challenge.imageBase64()),
				requestId(httpRequest));
	}

	@PostMapping("/password/change")
	public ApiResponse<String> changePassword(CurrentUser currentUser,
			@Valid @RequestBody PasswordChangeRequest request,
			HttpServletRequest httpRequest) {
		authService.changePassword(currentUser, request.oldPassword(), request.newPassword());
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
