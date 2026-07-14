package com.reelshort.backend.release;

/**
 * Update manifest returned to the Android app. Field names match the existing client DTO so the app
 * keeps working unchanged apart from relaxing URL validation for COS pre-signed URLs.
 */
public record UpdateManifestResponse(
		String versionName,
		long versionCode,
		String title,
		String releaseNotes,
		String publishedAt,
		String apkUrl,
		String sha256Url,
		long sizeBytes,
		long sha256SizeBytes,
		String apkSha256,
		long minimumVersionCode,
		boolean mandatory) {
}
