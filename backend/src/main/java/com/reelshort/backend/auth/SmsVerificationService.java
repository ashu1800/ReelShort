package com.reelshort.backend.auth;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SmsVerificationService {

	private static final int EXPIRES_IN_SECONDS = 120;

	private final SmsVerificationCodeRepository smsVerificationCodeRepository;
	private final SmsCallbackClient smsCallbackClient;
	private final TokenHasher tokenHasher;
	private final TransactionTemplate transactionTemplate;
	private final SecureRandom secureRandom = new SecureRandom();

	public SmsVerificationService(SmsVerificationCodeRepository smsVerificationCodeRepository,
			SmsCallbackClient smsCallbackClient, TokenHasher tokenHasher, TransactionTemplate transactionTemplate) {
		this.smsVerificationCodeRepository = smsVerificationCodeRepository;
		this.smsCallbackClient = smsCallbackClient;
		this.tokenHasher = tokenHasher;
		this.transactionTemplate = transactionTemplate;
	}

	public SmsSendResponse send(SmsVerificationPurpose purpose, PhoneIdentity phone) {
		OffsetDateTime now = OffsetDateTime.now();
		String rawCode = generateCode();
		SmsVerificationCode verificationCode = transactionTemplate.execute(status -> {
			smsVerificationCodeRepository.deleteStaleCodes(phone.e164(), purpose, now);
			smsVerificationCodeRepository.invalidateActiveCodes(phone.e164(), purpose, now);
			return smsVerificationCodeRepository.save(SmsVerificationCode.create(purpose, phone, rawCode, tokenHasher));
		});
		try {
			smsCallbackClient.send(new SmsCallbackMessage(
					"shortlink-sms-" + verificationCode.id(),
					phone.e164(),
					"Your ShortLink verification code is " + rawCode + ".",
					now));
		}
		catch (SmsAccountNotFoundException exception) {
			invalidateGeneratedCode(verificationCode);
			return new SmsSendResponse(EXPIRES_IN_SECONDS);
		}
		catch (RuntimeException exception) {
			invalidateGeneratedCode(verificationCode);
			throw new AuthException(400, "verification code delivery failed");
		}
		return new SmsSendResponse(EXPIRES_IN_SECONDS);
	}

	@Transactional
	public void verifyAndConsume(SmsVerificationPurpose purpose, PhoneIdentity phone, String code) {
		SmsVerificationCode verificationCode = smsVerificationCodeRepository
				.findByPhoneE164AndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(phone.e164(), purpose)
				.stream()
				.findFirst()
				.orElseThrow(() -> new AuthException(400, "invalid verification code"));
		OffsetDateTime now = OffsetDateTime.now();
		if (!verificationCode.isUsable(code, now, tokenHasher)) {
			throw new AuthException(400, "invalid verification code");
		}
		verificationCode.markUsed(now);
		smsVerificationCodeRepository.save(verificationCode);
	}

	private String generateCode() {
		return "%06d".formatted(secureRandom.nextInt(1_000_000));
	}

	private void invalidateGeneratedCode(SmsVerificationCode verificationCode) {
		transactionTemplate.executeWithoutResult(status -> {
			SmsVerificationCode freshCode = smsVerificationCodeRepository.findById(verificationCode.id())
					.orElse(verificationCode);
			freshCode.markUsed(OffsetDateTime.now());
			smsVerificationCodeRepository.save(freshCode);
		});
	}
}
