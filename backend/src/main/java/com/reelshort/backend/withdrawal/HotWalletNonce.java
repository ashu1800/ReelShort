package com.reelshort.backend.withdrawal;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "hot_wallet_nonces", uniqueConstraints = @UniqueConstraint(
		name = "uk_hot_wallet_nonces_wallet_chain", columnNames = {"network", "wallet_address", "chain_id"}))
class HotWalletNonce {

	@Id
	private UUID id;
	@Column(nullable = false, length = 16)
	private String network;
	@Column(name = "wallet_address", nullable = false, length = 128)
	private String walletAddress;
	@Column(name = "chain_id", nullable = false)
	private long chainId;
	@Column(name = "next_nonce", nullable = false, precision = 38, scale = 0)
	private BigInteger nextNonce;
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected HotWalletNonce() {
	}

	static HotWalletNonce create(String network, String walletAddress, long chainId, BigInteger observedNonce) {
		HotWalletNonce nonce = new HotWalletNonce();
		nonce.id = UUID.randomUUID();
		nonce.network = network;
		nonce.walletAddress = walletAddress.toLowerCase();
		nonce.chainId = chainId;
		nonce.nextNonce = observedNonce;
		nonce.updatedAt = OffsetDateTime.now();
		return nonce;
	}

	BigInteger allocate(BigInteger observedNonce) {
		BigInteger allocated = nextNonce.max(observedNonce);
		nextNonce = allocated.add(BigInteger.ONE);
		updatedAt = OffsetDateTime.now();
		return allocated;
	}
}
