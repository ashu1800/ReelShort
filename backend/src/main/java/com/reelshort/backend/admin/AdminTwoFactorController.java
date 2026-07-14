package com.reelshort.backend.admin;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.security.TotpService;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin two-factor authentication setup endpoints. TOTP is required for sensitive operations like
 * batch withdrawal payout. These endpoints let the admin bind a Google Authenticator-style TOTP
 * secret.
 */
@RestController
@RequestMapping("/api/admin/2fa")
public class AdminTwoFactorController {

	private final AdminUserRepository adminUserRepository;
	private final TotpService totpService;

	public AdminTwoFactorController(AdminUserRepository adminUserRepository, TotpService totpService) {
		this.adminUserRepository = adminUserRepository;
		this.totpService = totpService;
	}

	@GetMapping("/status")
	public ApiResponse<TwoFactorStatusResponse> status(CurrentAdmin currentAdmin, HttpServletRequest request) {
		AdminUser admin = loadAdmin(currentAdmin);
		return ApiResponse.success(new TwoFactorStatusResponse(admin.totpEnabled()), requestId(request));
	}

	@PostMapping("/setup")
	public ApiResponse<TwoFactorSetupResponse> setup(CurrentAdmin currentAdmin, HttpServletRequest request) {
		// Generate a new secret each setup attempt; only persisted on successful enable.
		String secret = totpService.generateSecret();
		String issuer = "ShortLink";
		String label = URLEncoder.encode(issuer + ":" + currentAdmin.username(), StandardCharsets.UTF_8);
		String otpauthUri = String.format("otpauth://totp/%s?secret=%s&issuer=%s&digits=6&period=30",
				label, secret, URLEncoder.encode(issuer, StandardCharsets.UTF_8));
		return ApiResponse.success(new TwoFactorSetupResponse(secret, otpauthUri), requestId(request));
	}

	@PostMapping("/enable")
	public ApiResponse<TwoFactorStatusResponse> enable(CurrentAdmin currentAdmin,
			@Valid @RequestBody TwoFactorEnableRequest enableRequest, HttpServletRequest request) {
		if (!totpService.verify(enableRequest.secret(), enableRequest.code())) {
			throw new AdminException(400, "invalid verification code");
		}
		AdminUser admin = loadAdmin(currentAdmin);
		admin.enableTotp(enableRequest.secret());
		adminUserRepository.save(admin);
		return ApiResponse.success(new TwoFactorStatusResponse(true), requestId(request));
	}

	private AdminUser loadAdmin(CurrentAdmin currentAdmin) {
		return adminUserRepository.findById(currentAdmin.adminUserId())
				.orElseThrow(() -> new AdminException(404, "admin not found"));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}

	public record TwoFactorStatusResponse(boolean enabled) {
	}

	public record TwoFactorSetupResponse(String secret, String otpauthUri) {
	}

	public record TwoFactorEnableRequest(
			@NotBlank @Size(max = 64) String secret,
			@NotBlank @Size(max = 6) String code) {
	}
}
