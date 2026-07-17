package com.reelshort.backend.wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_wallets")
public class UserWallet {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false, unique = true)
	private UUID userId;

	@Column(nullable = false, length = 16)
	private String network;

	@Column(name = "wallet_address", nullable = false, length = 128)
	private String walletAddress;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected UserWallet() {
	}

	private UserWallet(UUID id, UUID userId, String network, String walletAddress, OffsetDateTime createdAt,
			OffsetDateTime updatedAt) {
		this.id = id;
		this.userId = userId;
		this.network = network;
		this.walletAddress = walletAddress;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static UserWallet create(UUID userId, String network, String walletAddress) {
		OffsetDateTime now = OffsetDateTime.now();
		return new UserWallet(UUID.randomUUID(), userId, network, walletAddress, now, now);
	}

	public void replace(String network, String walletAddress) {
		this.network = network;
		this.walletAddress = walletAddress;
		this.updatedAt = OffsetDateTime.now();
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public String network() {
		return network;
	}

	public String walletAddress() {
		return walletAddress;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}
}
