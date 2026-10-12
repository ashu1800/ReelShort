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

	/**
	 * P2-16: 只保存 SHA-256 十六进制哈希，明文答案既不落库也不保存在实体里
	 * （V31 起 answer 列允许为空，保留该列仅用于兼容历史数据）。
	 */
	@Column(name = "answer_hash", length = 64)
	private String answerHash;

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

	private CaptchaChallenge(UUID id, String answerHash, String imageBase64, OffsetDateTime expiresAt,
			OffsetDateTime usedAt, OffsetDateTime createdAt) {
		this.id = id;
		this.answerHash = answerHash;
		this.imageBase64 = imageBase64;
		this.expiresAt = expiresAt;
		this.usedAt = usedAt;
		this.createdAt = createdAt;
	}

	public static CaptchaChallenge create(String answerHash, String imageBase64, OffsetDateTime expiresAt) {
		return new CaptchaChallenge(UUID.randomUUID(), answerHash, imageBase64, expiresAt, null, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public String answerHash() {
		return answerHash;
	}

	public String imageBase64() {
		return imageBase64;
	}

	public OffsetDateTime expiresAt() {
		return expiresAt;
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
