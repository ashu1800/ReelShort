package com.reelshort.backend.release;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.security.SecureTokenComparator;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Internal release management endpoints, protected by {@code X-Internal-Super-Token}. The publish
 * script calls {@code POST /api/internal/release/publish} after uploading the APK to COS.
 */
@RestController
@RequestMapping("/api/internal/release")
public class InternalReleaseController {

	private final ReleaseService releaseService;
	private final String superToken;

	public InternalReleaseController(ReleaseService releaseService,
			@Value("${reelshort.internal.super-token:}") String superToken) {
		this.releaseService = releaseService;
		this.superToken = superToken;
	}

	@PostMapping("/publish")
	public ApiResponse<PublishReleaseResponse> publish(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			@Valid @RequestBody PublishReleaseRequest request,
			HttpServletRequest servletRequest) {
		requireInternalToken(providedToken);
		AppRelease saved = releaseService.publish(request);
		return ApiResponse.success(toResponse(saved), requestId(servletRequest));
	}

	@GetMapping("/latest")
	public ApiResponse<UpdateManifestResponse> latest(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			HttpServletRequest servletRequest) {
		requireInternalToken(providedToken);
		Optional<UpdateManifestResponse> manifest = releaseService.latestManifest();
		if (manifest.isEmpty()) {
			throw new ReleaseException(404, "no release available");
		}
		return ApiResponse.success(manifest.get(), requestId(servletRequest));
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

	private PublishReleaseResponse toResponse(AppRelease release) {
		return new PublishReleaseResponse(release.versionName(), release.versionCode(), release.apkSha256(),
				release.apkSizeBytes(), true);
	}
}
