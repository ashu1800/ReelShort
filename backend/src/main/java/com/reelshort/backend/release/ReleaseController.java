package com.reelshort.backend.release;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
	private final CosPresignService cosPresignService;

	public ReleaseController(ReleaseService releaseService, CosPresignService cosPresignService) {
		this.releaseService = releaseService;
		this.cosPresignService = cosPresignService;
	}

	/**
	 * Current manifest path used by App >= 0.4.2.
	 */
	@GetMapping("/api/app/release/latest")
	public ResponseEntity<UpdateManifestResponse> latest() {
		return respond();
	}

	/**
	 * Stable download entry point for the official website. The public link never expires and never
	 * contains a COS URL; each request is redirected to a freshly signed COS URL, so links can be
	 * shared without handing out (or refreshing) a pre-signed address.
	 */
	@GetMapping("/api/app/release/latest/apk")
	public ResponseEntity<Void> download() {
		AppRelease release = releaseService.latestRelease()
				.orElseThrow(() -> new ReleaseException(404, "no release available"));
		String url = cosPresignService.presignDownload(release.apkObjectKey());
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(url))
				.cacheControl(CacheControl.noStore())
				.build();
	}

	private ResponseEntity<UpdateManifestResponse> respond() {
		Optional<UpdateManifestResponse> manifest = releaseService.latestManifest();
		if (manifest.isEmpty()) {
			throw new ReleaseException(404, "no release available");
		}
		return ResponseEntity.ok(manifest.get());
	}
}
