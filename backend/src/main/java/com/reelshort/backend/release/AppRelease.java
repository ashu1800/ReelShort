package com.reelshort.backend.release;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_releases")
public class AppRelease {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "version_name", nullable = false, length = 32)
	private String versionName;

	@Column(name = "version_code", nullable = false)
	private long versionCode;

	@Column(name = "apk_object_key", nullable = false, length = 512)
	private String apkObjectKey;

	@Column(name = "sha256_object_key", nullable = false, length = 512)
	private String sha256ObjectKey;

	@Column(name = "apk_size_bytes", nullable = false)
	private long apkSizeBytes;

	@Column(name = "sha256_size_bytes", nullable = false)
	private long sha256SizeBytes;

	@Column(name = "apk_sha256", nullable = false, length = 64)
	private String apkSha256;

	@Column(name = "title", nullable = false, length = 255)
	private String title;

	@Column(name = "release_notes", nullable = false, length = 2000)
	private String releaseNotes;

	@Column(name = "mandatory", nullable = false)
	private boolean mandatory;

	@Column(name = "minimum_version_code", nullable = false)
	private long minimumVersionCode;

	@Column(name = "published_at", nullable = false)
	private OffsetDateTime publishedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected AppRelease() {
	}

	private AppRelease(UUID id, String versionName, long versionCode, String apkObjectKey, String sha256ObjectKey,
			long apkSizeBytes, long sha256SizeBytes, String apkSha256, String title, String releaseNotes,
			boolean mandatory, long minimumVersionCode, OffsetDateTime publishedAt, OffsetDateTime createdAt) {
		this.id = id;
		this.versionName = versionName;
		this.versionCode = versionCode;
		this.apkObjectKey = apkObjectKey;
		this.sha256ObjectKey = sha256ObjectKey;
		this.apkSizeBytes = apkSizeBytes;
		this.sha256SizeBytes = sha256SizeBytes;
		this.apkSha256 = apkSha256;
		this.title = title;
		this.releaseNotes = releaseNotes;
		this.mandatory = mandatory;
		this.minimumVersionCode = minimumVersionCode;
		this.publishedAt = publishedAt;
		this.createdAt = createdAt;
	}

	public static AppRelease create(String versionName, long versionCode, String apkObjectKey, String sha256ObjectKey,
			long apkSizeBytes, long sha256SizeBytes, String apkSha256, String title, String releaseNotes,
			boolean mandatory, long minimumVersionCode) {
		OffsetDateTime now = OffsetDateTime.now();
		return new AppRelease(UUID.randomUUID(), versionName, versionCode, apkObjectKey, sha256ObjectKey, apkSizeBytes,
				sha256SizeBytes, apkSha256, title, releaseNotes, mandatory, minimumVersionCode, now, now);
	}

	public void republish(String apkObjectKey, String sha256ObjectKey, long apkSizeBytes, long sha256SizeBytes,
			String apkSha256, String title, String releaseNotes, boolean mandatory, long minimumVersionCode) {
		this.apkObjectKey = apkObjectKey;
		this.sha256ObjectKey = sha256ObjectKey;
		this.apkSizeBytes = apkSizeBytes;
		this.sha256SizeBytes = sha256SizeBytes;
		this.apkSha256 = apkSha256;
		this.title = title;
		this.releaseNotes = releaseNotes;
		this.mandatory = mandatory;
		this.minimumVersionCode = minimumVersionCode;
		this.publishedAt = OffsetDateTime.now();
	}

	public UUID id() {
		return id;
	}

	public String versionName() {
		return versionName;
	}

	public long versionCode() {
		return versionCode;
	}

	public String apkObjectKey() {
		return apkObjectKey;
	}

	public String sha256ObjectKey() {
		return sha256ObjectKey;
	}

	public long apkSizeBytes() {
		return apkSizeBytes;
	}

	public long sha256SizeBytes() {
		return sha256SizeBytes;
	}

	public String apkSha256() {
		return apkSha256;
	}

	public String title() {
		return title;
	}

	public String releaseNotes() {
		return releaseNotes;
	}

	public boolean mandatory() {
		return mandatory;
	}

	public long minimumVersionCode() {
		return minimumVersionCode;
	}

	public OffsetDateTime publishedAt() {
		return publishedAt;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
