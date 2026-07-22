package com.reelshort.backend.release;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
	public ResponseEntity<UpdateManifestResponse> latest() {
		return respond();
	}

	private ResponseEntity<UpdateManifestResponse> respond() {
		Optional<UpdateManifestResponse> manifest = releaseService.latestManifest();
		if (manifest.isEmpty()) {
			throw new ReleaseException(404, "no release available");
		}
		return ResponseEntity.ok(manifest.get());
	}
}
