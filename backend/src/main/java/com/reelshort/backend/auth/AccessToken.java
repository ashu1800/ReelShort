package com.reelshort.backend.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.reelshort.backend.user.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "access_tokens")
public class AccessToken {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(nullable = false)
	private OffsetDateTime issuedAt;

	protected AccessToken() {
	}

	private AccessToken(UUID id, String tokenHash, UserAccount user, OffsetDateTime issuedAt) {
		this.id = id;
		this.tokenHash = tokenHash;
		this.user = user;
		this.issuedAt = issuedAt;
	}

	public static AccessToken issue(String tokenHash, UserAccount user) {
		return new AccessToken(UUID.randomUUID(), tokenHash, user, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public String tokenHash() {
		return tokenHash;
	}

	public UserAccount user() {
		return user;
	}

	public OffsetDateTime issuedAt() {
		return issuedAt;
	}
}
