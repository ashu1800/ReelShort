package com.reelshort.backend.auth;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmsVerificationService {

	private static final int EXPIRES_IN_SECONDS = 120;

	private final SmsVerificationCodeRepository smsVerificationCodeRepository;

	public SmsVerificationService(SmsVerificationCodeRepository smsVerificationCodeRepository) {
		this.smsVerificationCodeRepository = smsVerificationCodeRepository;
	}

	@Transactional
	public SmsSendResponse send(SmsVerificationPurpose purpose, PhoneIdentity phone) {
		smsVerificationCodeRepository.save(SmsVerificationCode.create(purpose, phone));
		return new SmsSendResponse(EXPIRES_IN_SECONDS);
	}

	@Transactional
	public void verifyAndConsume(SmsVerificationPurpose purpose, PhoneIdentity phone, String code) {
		SmsVerificationCode verificationCode = smsVerificationCodeRepository
				.findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(phone.e164(), purpose)
				.orElseThrow(() -> new AuthException(400, "invalid verification code"));
		OffsetDateTime now = OffsetDateTime.now();
		if (!verificationCode.isUsable(code, now)) {
			throw new AuthException(400, "invalid verification code");
		}
		verificationCode.markUsed(now);
		smsVerificationCodeRepository.save(verificationCode);
	}
}
