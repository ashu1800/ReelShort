package com.reelshort.backend.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsVerificationCodeRepository extends JpaRepository<SmsVerificationCode, UUID> {

	Optional<SmsVerificationCode> findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(
			String phoneE164, SmsVerificationPurpose purpose);
}
