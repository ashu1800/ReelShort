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

	@Column(name = "phone_country_code", length = 8)
	private String phoneCountryCode;

	@Column(name = "phone_number", length = 32)
	private String phoneNumber;

	@Column(name = "phone_e164", unique = true, length = 32)
	private String phoneE164;

	@Column(nullable = false, length = 120)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private UserStatus status;

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	protected UserAccount() {
	}

	private UserAccount(UUID id, String username, String phoneCountryCode, String phoneNumber, String phoneE164,
			String passwordHash, UserStatus status, OffsetDateTime createdAt) {
		this.id = id;
		this.username = username;
		this.phoneCountryCode = phoneCountryCode;
		this.phoneNumber = phoneNumber;
		this.phoneE164 = phoneE164;
		this.passwordHash = passwordHash;
		this.status = status;
		this.createdAt = createdAt;
	}

	public static UserAccount create(String username, String passwordHash, UserStatus status) {
		return new UserAccount(UUID.randomUUID(), username, null, null, null, passwordHash, status, OffsetDateTime.now());
	}

	public static UserAccount createPhoneAccount(String phoneCountryCode, String phoneNumber, String phoneE164,
			String passwordHash) {
		return new UserAccount(UUID.randomUUID(), phoneE164, phoneCountryCode, phoneNumber, phoneE164,
				passwordHash, UserStatus.ACTIVE, OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public String username() {
		return username;
	}

	public String phoneCountryCode() {
		return phoneCountryCode;
	}

	public String phoneNumber() {
		return phoneNumber;
	}

	public String phoneE164() {
		return phoneE164;
	}

	public String passwordHash() {
		return passwordHash;
	}

	public void changePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public UserStatus status() {
		return status;
	}

	public void changeStatus(UserStatus status) {
		this.status = status;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
