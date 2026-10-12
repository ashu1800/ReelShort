package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * P2-16: 验证码答案哈希化、一次性消费、答错即作废。
 */
@SpringBootTest
class CaptchaServiceTests {

	@Autowired
	private CaptchaService captchaService;

	@Autowired
	private CaptchaChallengeRepository captchaChallengeRepository;

	@Autowired
	private TokenHasher tokenHasher;

	@Test
	void storesAnswerHashInsteadOfPlaintext() {
		CaptchaService.GeneratedCaptcha generated = captchaService.generate();

		CaptchaChallenge stored = captchaChallengeRepository.findById(UUID.fromString(generated.captchaId()))
				.orElseThrow();

		assertThat(stored.answerHash()).isNotBlank().isNotEqualTo(generated.answer());
		assertThat(stored.answerHash()).isEqualTo(tokenHasher.hash(generated.answer().toUpperCase(Locale.ROOT)));
		assertThat(stored.answerHash()).hasSize(64);
	}

	@Test
	void generatedAnswerIsUppercaseAndUsesUnambiguousAlphabet() {
		CaptchaService.GeneratedCaptcha generated = captchaService.generate();

		assertThat(generated.answer()).hasSize(4).matches("[A-Z2-9]{4}");
		assertThat(generated.answer()).doesNotContain("I", "O", "0", "1");
		assertThat(generated.imageBase64()).startsWith("data:image/png;base64,");
	}

	@Test
	void verifiesAnswerCaseInsensitivelyAndConsumesItOnce() {
		CaptchaService.GeneratedCaptcha generated = captchaService.generate();

		assertThatCode(() -> captchaService.verifyAndConsume(generated.captchaId(),
				" " + generated.answer().toLowerCase(Locale.ROOT) + " ")).doesNotThrowAnyException();

		assertThatThrownBy(() -> captchaService.verifyAndConsume(generated.captchaId(), generated.answer()))
				.isInstanceOf(AuthException.class)
				.hasMessage("captcha expired")
				.extracting("statusCode")
				.isEqualTo(400);
	}

	@Test
	void wrongAnswerInvalidatesChallengeImmediately() {
		CaptchaService.GeneratedCaptcha generated = captchaService.generate();
		String wrongAnswer = generated.answer().equals("ZZZZ") ? "YYYY" : "ZZZZ";

		assertThatThrownBy(() -> captchaService.verifyAndConsume(generated.captchaId(), wrongAnswer))
				.isInstanceOf(AuthException.class)
				.hasMessage("captcha verification failed")
				.extracting("statusCode")
				.isEqualTo(400);

		// 4 位验证码只给一次机会：答错后正确答案同样被拒（used_at 已提交）
		assertThat(captchaChallengeRepository.findById(UUID.fromString(generated.captchaId())).orElseThrow().isUsed())
				.isTrue();
		assertThatThrownBy(() -> captchaService.verifyAndConsume(generated.captchaId(), generated.answer()))
				.isInstanceOf(AuthException.class)
				.hasMessage("captcha expired");
	}

	@Test
	void rejectsExpiredChallenge() {
		CaptchaChallenge expired = captchaChallengeRepository.save(CaptchaChallenge.create(tokenHasher.hash("ABCD"),
				"data:image/png;base64,AA==", OffsetDateTime.now().minusMinutes(1)));

		assertThatThrownBy(() -> captchaService.verifyAndConsume(expired.id().toString(), "ABCD"))
				.isInstanceOf(AuthException.class)
				.hasMessage("captcha expired");
	}

	@Test
	void rejectsUnknownOrMalformedCaptchaId() {
		assertThatThrownBy(() -> captchaService.verifyAndConsume(UUID.randomUUID().toString(), "ABCD"))
				.isInstanceOf(AuthException.class)
				.hasMessage("captcha verification failed");
		assertThatThrownBy(() -> captchaService.verifyAndConsume("not-a-uuid", "ABCD"))
				.isInstanceOf(AuthException.class)
				.hasMessage("captcha verification failed");
		assertThatThrownBy(() -> captchaService.verifyAndConsume(null, "ABCD"))
				.isInstanceOf(AuthException.class)
				.hasMessage("captcha verification failed");
	}
}
