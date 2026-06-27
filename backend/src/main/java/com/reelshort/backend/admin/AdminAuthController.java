package com.reelshort.backend.admin;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

	public AdminAuthController(AdminAuthService adminAuthService) {
		this.adminAuthService = adminAuthService;
	}

	@PostMapping("/login")
	public ApiResponse<AdminAuthTokenResponse> login(@Valid @RequestBody AdminLoginRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(adminAuthService.login(request.username(), request.password()), requestId(httpRequest));
	}

	@PostMapping("/logout")
	public ApiResponse<String> logout(HttpServletRequest httpRequest) {
		adminAuthService.logout(extractBearerToken(httpRequest));
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
