package com.reelshort.backend.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	public ApiResponse<AuthToken> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
		return ApiResponse.success(authService.register(request.username(), request.password()), requestId(httpRequest));
	}

	@PostMapping("/login")
	public ApiResponse<AuthToken> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return ApiResponse.success(authService.login(request.username(), request.password()), requestId(httpRequest));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
