package com.reelshort.backend.withdrawal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.security.SecureTokenComparator;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Operations-side withdrawal config query, protected by {@code X-Internal-Super-Token}. Returns the
 * current minimum-points and minimum-USDT thresholds plus the full conversion snapshot, without any
 * user-specific data.
 */
@RestController
@RequestMapping("/api/internal/withdrawal")
public class InternalWithdrawalController {

	private final WithdrawalService withdrawalService;
	private final String superToken;

	public InternalWithdrawalController(WithdrawalService withdrawalService,
			@Value("${reelshort.internal.super-token:}") String superToken) {
		this.withdrawalService = withdrawalService;
		this.superToken = superToken;
	}

	@GetMapping("/thresholds")
	public ApiResponse<WithdrawalConversion.Snapshot> thresholds(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			HttpServletRequest request) {
		requireInternalToken(providedToken);
		return ApiResponse.success(withdrawalService.thresholds(), requestId(request));
	}

	private void requireInternalToken(String providedToken) {
		if (providedToken == null || providedToken.isBlank()) {
			throw new AuthException(401, "unauthorized");
		}
		if (superToken.isBlank() || !SecureTokenComparator.equals(superToken, providedToken)) {
			throw new AuthException(403, "forbidden");
		}
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
