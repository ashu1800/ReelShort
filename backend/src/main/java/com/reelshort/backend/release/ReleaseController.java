package com.reelshort.backend.release;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * App-facing update endpoint. Returns the latest release manifest with short-lived COS pre-signed
 * download URLs. Accessible to guests (no App Bearer token required).
 *
 * <p>These endpoints return the {@link UpdateManifestResponse} directly (NOT wrapped in
 * {@code ApiResponse}), because the Android {@code ShortLinkUpdateClient} deserializes the manifest
 * fields from the top level of the JSON body.
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
	public ResponseEntity<UpdateManifestResponse> latest(HttpServletRequest request) {
		return respond(request);
	}

	/**
	 * Legacy alias for App <= 0.4.1, which still polls the old nginx-served path. Kept until all
	 * installed clients have upgraded to the {@code /api/app/release/latest} endpoint.
	 */
	@GetMapping("/api/app/update/latest")
	public ResponseEntity<UpdateManifestResponse> legacyLatest(HttpServletRequest request) {
		Optional<UpdateManifestResponse> manifest = releaseService.latestLegacyManifest();
		if (manifest.isEmpty()) {
			throw new ReleaseException(404, "no release available");
		}
		return ResponseEntity.ok(manifest.get());
	}

	private ResponseEntity<UpdateManifestResponse> respond(HttpServletRequest request) {
		Optional<UpdateManifestResponse> manifest = releaseService.latestManifest();
		if (manifest.isEmpty()) {
			throw new ReleaseException(404, "no release available");
		}
		return ResponseEntity.ok(manifest.get());
	}
}
