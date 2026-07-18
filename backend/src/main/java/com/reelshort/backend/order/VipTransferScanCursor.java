package com.reelshort.backend.order;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "vip_transfer_scan_cursors", uniqueConstraints = @UniqueConstraint(
		name = "uk_vip_transfer_scan_cursor_snapshot",
		columnNames = { "receiving_wallet_address", "token_contract_address" }))
public class VipTransferScanCursor {
	@Id
	private UUID id;

	@Column(name = "receiving_wallet_address", nullable = false, length = 128)
	private String receivingWalletAddress;

	@Column(name = "token_contract_address", nullable = false, length = 128)
	private String tokenContractAddress;

	@Column(length = 512)
	private String fingerprint;

	@Column(name = "scan_window_started_at", nullable = false)
	private OffsetDateTime scanWindowStartedAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected VipTransferScanCursor() {
	}

	static VipTransferScanCursor create(String address, String contract, OffsetDateTime window) {
		VipTransferScanCursor cursor = new VipTransferScanCursor();
		cursor.id = UUID.randomUUID();
		cursor.receivingWalletAddress = address;
		cursor.tokenContractAddress = contract;
		cursor.scanWindowStartedAt = window;
		cursor.updatedAt = OffsetDateTime.now();
		return cursor;
	}

	void reset(OffsetDateTime window) {
		this.fingerprint = null;
		this.scanWindowStartedAt = window;
		this.updatedAt = OffsetDateTime.now();
	}

	void advance(String fingerprint) {
		if (fingerprint != null && fingerprint.length() > 512) {
			throw new IllegalArgumentException("pagination fingerprint exceeds 512 characters");
		}
		this.fingerprint = fingerprint;
		this.updatedAt = OffsetDateTime.now();
	}

	public UUID id() { return id; }
	public String fingerprint() { return fingerprint; }
	public OffsetDateTime scanWindowStartedAt() { return scanWindowStartedAt; }
}
