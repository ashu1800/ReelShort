package com.reelshort.backend.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "captcha_challenges")
public class CaptchaChallenge {

	@Id
	private UUID id;

	@Column(nullable = false, length = 8)
	private String answer;

	@Column(name = "image_base64", nullable = false, columnDefinition = "text")
	private String imageBase64;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "used_at")
	private OffsetDateTime usedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected CaptchaChallenge() {
	}

	private CaptchaChallenge(UUID id, String answer, String imageBase64, OffsetDateTime expiresAt,
			OffsetDateTime usedAt, OffsetDateTime createdAt) {
		this.id = id;
		this.answer = answer;
		this.imageBase64 = imageBase64;
		this.expiresAt = expiresAt;
		this.usedAt = usedAt;
		this.createdAt = createdAt;
	}

	public static CaptchaChallenge create(String answer, String imageBase64, OffsetDateTime expiresAt) {
		return new CaptchaChallenge(UUID.randomUUID(), answer, imageBase64, expiresAt, null, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public String answer() {
		return answer;
	}

	public String imageBase64() {
		return imageBase64;
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public boolean isExpired() {
		return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
	}

	public void markUsed() {
		this.usedAt = OffsetDateTime.now();
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
