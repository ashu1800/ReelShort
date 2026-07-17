package com.reelshort.backend.operations;

import java.util.UUID;

import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.security.SecureTokenComparator;
import com.reelshort.backend.system.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/operations/users/{userId}")
public class InternalOperationsController {

	private final InternalOperationsService internalOperationsService;
	private final String superToken;

	public InternalOperationsController(InternalOperationsService internalOperationsService,
			@Value("${reelshort.internal.super-token:}") String superToken) {
		this.internalOperationsService = internalOperationsService;
		this.superToken = superToken;
	}

	@GetMapping("/points/account")
	public ApiResponse<InternalPointsAccountResponse> pointsAccount(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			@PathVariable UUID userId,
			HttpServletRequest request) {
		requireInternalToken(providedToken);
		return ApiResponse.success(internalOperationsService.pointsAccount(userId), requestId(request));
	}

	@GetMapping("/watch-reward-task")
	public ApiResponse<InternalWatchRewardTaskResponse> watchRewardTask(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			@PathVariable UUID userId,
			HttpServletRequest request) {
		requireInternalToken(providedToken);
		return ApiResponse.success(internalOperationsService.watchRewardTask(userId), requestId(request));
	}

	@PostMapping("/watch-progress")
	public ApiResponse<InternalWatchProgressResponse> watchProgress(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			@PathVariable UUID userId,
			@Valid @RequestBody InternalWatchProgressRequest progressRequest,
			HttpServletRequest request) {
		requireInternalToken(providedToken);
		return ApiResponse.success(internalOperationsService.reportWatchProgress(userId, progressRequest),
				requestId(request));
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
