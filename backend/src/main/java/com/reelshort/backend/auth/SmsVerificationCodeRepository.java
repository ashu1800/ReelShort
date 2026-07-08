package com.reelshort.backend.auth;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface SmsVerificationCodeRepository extends JpaRepository<SmsVerificationCode, UUID> {

	Optional<SmsVerificationCode> findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(
			String phoneE164, SmsVerificationPurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<SmsVerificationCode> findByPhoneE164AndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
			String phoneE164, SmsVerificationPurpose purpose);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			delete from SmsVerificationCode code
			 where code.phoneE164 = :phoneE164
			   and code.purpose = :purpose
			   and (code.usedAt is not null or code.expiresAt < :now)
			""")
	int deleteStaleCodes(String phoneE164, SmsVerificationPurpose purpose, OffsetDateTime now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update SmsVerificationCode code
			   set code.usedAt = :now
			 where code.phoneE164 = :phoneE164
			   and code.purpose = :purpose
			   and code.usedAt is null
			""")
	int invalidateActiveCodes(String phoneE164, SmsVerificationPurpose purpose, OffsetDateTime now);
}
