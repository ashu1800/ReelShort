package com.reelshort.backend.auth;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.auth.CaptchaChallenge;
import com.reelshort.backend.auth.CaptchaChallengeRepository;

/**
 * Generates and verifies image-based captchas for registration anti-bot protection. Generates a
 * 4-character alphanumeric captcha as a Base64-encoded PNG, stored in the DB with a 5-minute expiry.
 *
 * P2-16: 答案只以 SHA-256 十六进制哈希落库，校验时对输入做同样哈希后比较；答错立即作废（一次性机会）。
 */
@Service
public class CaptchaService {

	private static final int CAPTCHA_LENGTH = 4;
	private static final int EXPIRY_MINUTES = 5;
	private static final int IMAGE_WIDTH = 160;
	private static final int IMAGE_HEIGHT = 50;
	private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no confusing chars (I,O,0,1)

	private final SecureRandom secureRandom = new SecureRandom();
	private final CaptchaChallengeRepository captchaRepository;
	private final TokenHasher tokenHasher;

	public CaptchaService(CaptchaChallengeRepository captchaRepository, TokenHasher tokenHasher) {
		this.captchaRepository = captchaRepository;
		this.tokenHasher = tokenHasher;
	}

	/**
	 * P2-16: 生成结果。明文答案只在内存中交给调用方（绘制图片时已使用），不会落库；
	 * 因此刻意不放在实体里，避免实体被序列化或持久化时泄露。
	 */
	public record GeneratedCaptcha(String captchaId, String imageBase64, String answer) {
	}

	/**
	 * 生成挑战时同样使用 REQUIRES_NEW：挑战行必须在 {@link #verifyAndConsume} 的独立事务里可见。
	 * 若沿用调用方事务（例如 {@code @Transactional} 的测试或注册外层事务），挑战会一直未提交，
	 * 而校验走 REQUIRES_NEW 读不到该行，表现为"验证码必然校验失败"。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public GeneratedCaptcha generate() {
		String answer = randomAnswer();
		String imageBase64 = renderImage(answer);
		CaptchaChallenge challenge = CaptchaChallenge.create(hashAnswer(answer), imageBase64,
				OffsetDateTime.now().plusMinutes(EXPIRY_MINUTES));
		captchaRepository.save(challenge);
		return new GeneratedCaptcha(challenge.id().toString(), challenge.imageBase64(), answer);
	}

	/**
	 * P2-16: 使用 REQUIRES_NEW，让"消费成功"与"答错作废"的 used_at 在调用方事务回滚时也能落库
	 * （注册失败、用户名重复等场景下 4 位验证码不允许被复用）。悲观锁仍在同一事务内，
	 * 并发双花保护不变。noRollbackFor 保证校验失败时抛出的 AuthException 不会回滚已写入的 used_at。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = AuthException.class)
	public void verifyAndConsume(String captchaId, String answer) {
		if (captchaId == null || answer == null) {
			throw new AuthException(400, "captcha verification failed");
		}
		UUID id;
		try {
			id = UUID.fromString(captchaId);
		}
		catch (IllegalArgumentException exception) {
			throw new AuthException(400, "captcha verification failed");
		}
		// M5: 使用悲观锁读取，防止并发双花（两个请求同时读到 usedAt=null 都通过验证）
		CaptchaChallenge challenge = captchaRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new AuthException(400, "captcha verification failed"));
		if (challenge.isUsed() || challenge.isExpired()) {
			throw new AuthException(400, "captcha expired");
		}
		if (challenge.answerHash() == null || !challenge.answerHash().equals(hashAnswer(answer))) {
			// P2-16: 4 位验证码只给一次机会，答错立即作废，避免在同一 captchaId 上暴力枚举
			challenge.markUsed();
			captchaRepository.save(challenge);
			throw new AuthException(400, "captcha verification failed");
		}
		challenge.markUsed();
		captchaRepository.save(challenge);
	}

	/**
	 * 校验用的规范化：去空白 + 统一大写（大小写不敏感语义与改造前一致）。
	 */
	private String hashAnswer(String answer) {
		return tokenHasher.hash(answer.trim().toUpperCase(Locale.ROOT));
	}

	private String randomAnswer() {
		StringBuilder sb = new StringBuilder(CAPTCHA_LENGTH);
		for (int i = 0; i < CAPTCHA_LENGTH; i++) {
			// P2-16: 答案必须不可预测，使用 SecureRandom（噪声绘制仍可用普通随机）
			sb.append(CHARS.charAt(secureRandom.nextInt(CHARS.length())));
		}
		return sb.toString();
	}

	private String renderImage(String answer) {
		BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setColor(new Color(17, 21, 30));
			g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
			g.setFont(new Font("Arial", Font.BOLD, 28));
			ThreadLocalRandom rng = ThreadLocalRandom.current();
			for (int i = 0; i < answer.length(); i++) {
				g.setColor(new Color(rng.nextInt(180, 256), rng.nextInt(180, 256), rng.nextInt(100, 200)));
				int x = 15 + i * 33 + rng.nextInt(-5, 5);
				int y = 35 + rng.nextInt(-5, 5);
				g.drawString(String.valueOf(answer.charAt(i)), x, y);
			}
			// Add noise lines
			for (int i = 0; i < 4; i++) {
				g.setColor(new Color(rng.nextInt(60, 120), rng.nextInt(60, 120), rng.nextInt(60, 120)));
				g.drawLine(rng.nextInt(IMAGE_WIDTH), rng.nextInt(IMAGE_HEIGHT),
						rng.nextInt(IMAGE_WIDTH), rng.nextInt(IMAGE_HEIGHT));
			}
		}
		finally {
			g.dispose();
		}
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(image, "png", baos);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
		}
		catch (Exception exception) {
			throw new IllegalStateException("captcha image generation failed", exception);
		}
	}
}
