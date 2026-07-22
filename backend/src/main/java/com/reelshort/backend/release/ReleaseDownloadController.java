package com.reelshort.backend.release;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stable download path kept for App 0.4.x. Old clients reject COS pre-signed URLs in the manifest,
 * so they first request this first-party URL and are then redirected to the short-lived COS URL.
 */
@RestController
public class ReleaseDownloadController {

	private final ReleaseService releaseService;

	public ReleaseDownloadController(ReleaseService releaseService) {
		this.releaseService = releaseService;
	}

	@GetMapping("/downloads/android/{fileName:.+}")
	public ResponseEntity<Void> download(@PathVariable String fileName) {
		Optional<String> downloadUrl = releaseService.legacyAssetDownloadUrl(fileName);
		if (downloadUrl.isEmpty()) {
			throw new ReleaseException(404, "release asset not found");
		}
		return redirect(downloadUrl.get());
	}

	private ResponseEntity<Void> redirect(String url) {
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
	}
}
