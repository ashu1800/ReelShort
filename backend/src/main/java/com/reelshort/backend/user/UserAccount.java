package com.reelshort.backend.user;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserAccount {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 64)
	private String username;

	@Column(nullable = false, length = 120)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private UserStatus status;

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	protected UserAccount() {
	}

	private UserAccount(UUID id, String username, String passwordHash, UserStatus status, OffsetDateTime createdAt) {
		this.id = id;
		this.username = username;
		this.passwordHash = passwordHash;
		this.status = status;
		this.createdAt = createdAt;
	}

	public static UserAccount create(String username, String passwordHash, UserStatus status) {
		return new UserAccount(UUID.randomUUID(), username, passwordHash, status, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public String username() {
		return username;
	}

	public String passwordHash() {
		return passwordHash;
	}

	public UserStatus status() {
		return status;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
