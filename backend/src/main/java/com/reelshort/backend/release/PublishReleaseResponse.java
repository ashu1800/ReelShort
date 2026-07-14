package com.reelshort.backend.release;

/**
 * Response to the internal publish endpoint, exposing only the fields the release script or admin
 * tooling need to confirm a publish succeeded. Avoids leaking the full JPA entity.
 */
public record PublishReleaseResponse(
		String versionName,
		long versionCode,
		String apkSha256,
		long apkSizeBytes,
		boolean published) {
}
