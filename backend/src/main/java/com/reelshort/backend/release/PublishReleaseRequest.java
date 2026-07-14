package com.reelshort.backend.release;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Internal publish request body submitted by the release script after uploading the APK to COS.
 */
public record PublishReleaseRequest(
		@NotBlank @Pattern(regexp = "\\d+\\.\\d+\\.\\d+", message = "versionName must use X.Y.Z") String versionName,
		@Min(1) long versionCode,
		@NotBlank @Size(max = 512) String apkObjectKey,
		@NotBlank @Size(max = 512) String sha256ObjectKey,
		@Min(1) long apkSizeBytes,
		@Min(1) long sha256SizeBytes,
		@NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}", message = "apkSha256 must be 64 hex chars") String apkSha256,
		@Size(max = 255) String title,
		@Size(max = 2000) String releaseNotes,
		boolean mandatory,
		@Min(0) long minimumVersionCode) {
}
