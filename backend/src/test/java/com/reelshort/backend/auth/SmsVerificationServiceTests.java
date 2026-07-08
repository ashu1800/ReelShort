package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SmsVerificationServiceTests {

	@Autowired
	private SmsVerificationService smsVerificationService;

	@Autowired
	private SmsVerificationCodeRepository smsVerificationCodeRepository;

	@Autowired
	private PhoneNumberNormalizer phoneNumberNormalizer;

	@Test
	void resendingSamePurposeInvalidatesPreviousCode() {
		PhoneIdentity phone = phoneNumberNormalizer.normalize("+1", "4155550401");
		smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone);
		SmsVerificationCode firstCode = smsVerificationCodeRepository
				.findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(phone.e164(), SmsVerificationPurpose.PUBLIC_REGISTER)
				.orElseThrow();

		smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone);

		SmsVerificationCode staleCode = smsVerificationCodeRepository.findById(firstCode.id()).orElseThrow();
		assertThat(staleCode.isUsable("000000", OffsetDateTime.now())).isFalse();
	}

	@Test
	void verificationCodeCanOnlyBeConsumedOnce() {
		PhoneIdentity phone = phoneNumberNormalizer.normalize("+1", "4155550402");
		smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone);

		smsVerificationService.verifyAndConsume(SmsVerificationPurpose.PUBLIC_REGISTER, phone, "000000");

		assertThatThrownBy(() ->
				smsVerificationService.verifyAndConsume(SmsVerificationPurpose.PUBLIC_REGISTER, phone, "000000"))
				.isInstanceOf(AuthException.class)
				.extracting("statusCode")
				.isEqualTo(400);
	}
}
