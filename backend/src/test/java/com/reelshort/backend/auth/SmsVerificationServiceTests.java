package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.mockito.ArgumentCaptor;

@SpringBootTest
@Transactional
class SmsVerificationServiceTests {

	private static final Pattern CODE_PATTERN = Pattern.compile("code is (\\d{6})\\.");

	@Autowired
	private SmsVerificationService smsVerificationService;

	@Autowired
	private SmsVerificationCodeRepository smsVerificationCodeRepository;

	@Autowired
	private PhoneNumberNormalizer phoneNumberNormalizer;

	@Autowired
	private TokenHasher tokenHasher;

	@MockitoBean
	private SmsCallbackClient smsCallbackClient;

	@Test
	void sendsRandomSixDigitCodeThroughCallbackAndStoresOnlyHash() {
		PhoneIdentity phone = phoneNumberNormalizer.normalize("+1", "4155550400");
		smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone);

		ArgumentCaptor<SmsCallbackMessage> messageCaptor = ArgumentCaptor.forClass(SmsCallbackMessage.class);
		verify(smsCallbackClient).send(messageCaptor.capture());
		SmsCallbackMessage callbackMessage = messageCaptor.getValue();
		Matcher matcher = CODE_PATTERN.matcher(callbackMessage.content());
		assertThat(matcher.find()).isTrue();
		String rawCode = matcher.group(1);
		assertThat(rawCode).matches("\\d{6}");
		assertThat(callbackMessage.phone()).isEqualTo(phone.e164());
		assertThat(callbackMessage.supplierMessageId()).startsWith("shortlink-sms-");

		SmsVerificationCode storedCode = smsVerificationCodeRepository
				.findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(phone.e164(), SmsVerificationPurpose.PUBLIC_REGISTER)
				.orElseThrow();
		assertThat(storedCode.codeHash()).isNotBlank();
		assertThat(storedCode.codeHash()).doesNotContain(rawCode);

		smsVerificationService.verifyAndConsume(SmsVerificationPurpose.PUBLIC_REGISTER, phone, rawCode);
		assertThatThrownBy(() -> smsVerificationService
				.verifyAndConsume(SmsVerificationPurpose.PUBLIC_REGISTER, phone, rawCode))
				.isInstanceOf(AuthException.class)
				.extracting("statusCode")
				.isEqualTo(400);
	}

	@Test
	void resendingSamePurposeInvalidatesPreviousCode() {
		PhoneIdentity phone = phoneNumberNormalizer.normalize("+1", "4155550401");
		smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone);
		SmsVerificationCode firstCode = smsVerificationCodeRepository
				.findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(phone.e164(), SmsVerificationPurpose.PUBLIC_REGISTER)
				.orElseThrow();

		smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone);

		SmsVerificationCode staleCode = smsVerificationCodeRepository.findById(firstCode.id()).orElseThrow();
		assertThat(staleCode.isUsable("000000", OffsetDateTime.now(), tokenHasher)).isFalse();
	}

	@Test
	void verificationCodeCanOnlyBeConsumedOnce() {
		PhoneIdentity phone = phoneNumberNormalizer.normalize("+1", "4155550402");
		smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone);

		ArgumentCaptor<SmsCallbackMessage> messageCaptor = ArgumentCaptor.forClass(SmsCallbackMessage.class);
		verify(smsCallbackClient).send(messageCaptor.capture());
		String rawCode = extractCode(messageCaptor.getValue().content());

		smsVerificationService.verifyAndConsume(SmsVerificationPurpose.PUBLIC_REGISTER, phone, rawCode);

		assertThatThrownBy(() ->
				smsVerificationService.verifyAndConsume(SmsVerificationPurpose.PUBLIC_REGISTER, phone, rawCode))
				.isInstanceOf(AuthException.class)
				.extracting("statusCode")
				.isEqualTo(400);
	}

	@Test
	void callbackFailureMakesSendFailAndGeneratedCodeUnusable() {
		PhoneIdentity phone = phoneNumberNormalizer.normalize("+1", "4155550403");
		doThrow(new SmsCallbackException("account manager rejected sms"))
				.when(smsCallbackClient)
				.send(any(SmsCallbackMessage.class));

		assertThatThrownBy(() -> smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone))
				.isInstanceOf(AuthException.class)
				.hasMessage("verification code delivery failed")
				.extracting("statusCode")
				.isEqualTo(400);

		SmsVerificationCode storedCode = smsVerificationCodeRepository
				.findFirstByPhoneE164AndPurposeOrderByCreatedAtDesc(phone.e164(), SmsVerificationPurpose.PUBLIC_REGISTER)
				.orElseThrow();
		assertThat(storedCode.isUsable("000000", OffsetDateTime.now(), tokenHasher)).isFalse();
	}

	@Test
	void runtimeCallbackFailureIsStillReturnedAsDeliveryFailure() {
		PhoneIdentity phone = phoneNumberNormalizer.normalize("+1", "4155550404");
		doThrow(new IllegalStateException("callback transport failed"))
				.when(smsCallbackClient)
				.send(any(SmsCallbackMessage.class));

		assertThatThrownBy(() -> smsVerificationService.send(SmsVerificationPurpose.PUBLIC_REGISTER, phone))
				.isInstanceOf(AuthException.class)
				.hasMessage("verification code delivery failed")
				.extracting("statusCode")
				.isEqualTo(400);
	}

	private static String extractCode(String content) {
		Matcher matcher = CODE_PATTERN.matcher(content);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
