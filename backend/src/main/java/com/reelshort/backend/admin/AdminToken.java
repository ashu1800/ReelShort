package com.reelshort.backend.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_tokens")
public class AdminToken {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "admin_user_id")
	private UUID adminUserId;

	@Column(nullable = false, length = 64)
	private String username;

	@Column(nullable = false)
	private OffsetDateTime issuedAt;

	@Column(nullable = false)
	private OffsetDateTime expiresAt;

	protected AdminToken() {
	}

	private AdminToken(UUID id, String tokenHash, UUID adminUserId, String username, OffsetDateTime issuedAt,
			OffsetDateTime expiresAt) {
		this.id = id;
		this.tokenHash = tokenHash;
		this.adminUserId = adminUserId;
		this.username = username;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
	}

	public static AdminToken issue(String tokenHash, UUID adminUserId, String username, OffsetDateTime expiresAt) {
		return new AdminToken(UUID.randomUUID(), tokenHash, adminUserId, username, OffsetDateTime.now(), expiresAt);
	}

	public UUID adminUserId() {
		return adminUserId;
	}

	public String username() {
		return username;
	}

	public OffsetDateTime expiresAt() {
		return expiresAt;
	}
}
