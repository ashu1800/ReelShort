package com.reelshort.backend.release;

import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * App-facing update endpoint. Returns the latest release manifest with short-lived COS pre-signed
 * download URLs. Accessible to guests (no App Bearer token required).
 */
@RestController
public class ReleaseController {

	private final ReleaseService releaseService;

	public ReleaseController(ReleaseService releaseService) {
		this.releaseService = releaseService;
	}

	/**
	 * Current manifest path used by App >= 0.4.2.
	 */
	@GetMapping("/api/app/release/latest")
	public ApiResponse<UpdateManifestResponse> latest(HttpServletRequest request) {
		return respond(request);
	}

	/**
	 * Legacy alias for App <= 0.4.1, which still polls the old nginx-served path. Kept until all
	 * installed clients have upgraded to the {@code /api/app/release/latest} endpoint.
	 */
	@GetMapping("/api/app/update/latest")
	public ApiResponse<UpdateManifestResponse> legacyLatest(HttpServletRequest request) {
		return respond(request);
	}

	private ApiResponse<UpdateManifestResponse> respond(HttpServletRequest request) {
		Optional<UpdateManifestResponse> manifest = releaseService.latestManifest();
		if (manifest.isEmpty()) {
			throw new ReleaseException(404, "no release available");
		}
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(manifest.get(), requestId);
	}
}
