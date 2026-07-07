package com.reelshort.backend.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sms_verification_codes")
public class SmsVerificationCode {

	static final String SIMULATED_CODE = "000000";

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 48)
	private SmsVerificationPurpose purpose;

	@Column(name = "phone_country_code", nullable = false, length = 8)
	private String phoneCountryCode;

	@Column(name = "phone_number", nullable = false, length = 32)
	private String phoneNumber;

	@Column(name = "phone_e164", nullable = false, length = 32)
	private String phoneE164;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "used_at")
	private OffsetDateTime usedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected SmsVerificationCode() {
	}

	private SmsVerificationCode(UUID id, SmsVerificationPurpose purpose, PhoneIdentity phone,
			OffsetDateTime expiresAt, OffsetDateTime createdAt) {
		this.id = id;
		this.purpose = purpose;
		this.phoneCountryCode = phone.countryCode();
		this.phoneNumber = phone.phoneNumber();
		this.phoneE164 = phone.e164();
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public static SmsVerificationCode create(SmsVerificationPurpose purpose, PhoneIdentity phone) {
		OffsetDateTime now = OffsetDateTime.now();
		return new SmsVerificationCode(UUID.randomUUID(), purpose, phone, now.plusSeconds(120), now);
	}

	public boolean isUsable(String code, OffsetDateTime now) {
		return usedAt == null && !expiresAt.isBefore(now) && SIMULATED_CODE.equals(code);
	}

	public void markUsed(OffsetDateTime usedAt) {
		this.usedAt = usedAt;
	}

	public UUID id() {
		return id;
	}

	public SmsVerificationPurpose purpose() {
		return purpose;
	}

	public String phoneE164() {
		return phoneE164;
	}
}
