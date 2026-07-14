package com.reelshort.backend.auth;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.auth.CaptchaChallenge;
import com.reelshort.backend.auth.CaptchaChallengeRepository;

/**
 * Generates and verifies image-based captchas for registration anti-bot protection. Generates a
 * 4-character alphanumeric captcha as a Base64-encoded PNG, stored in the DB with a 5-minute expiry.
 */
@Service
public class CaptchaService {

	private static final int CAPTCHA_LENGTH = 4;
	private static final int EXPIRY_MINUTES = 5;
	private static final int IMAGE_WIDTH = 160;
	private static final int IMAGE_HEIGHT = 50;
	private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no confusing chars (I,O,0,1)

	private final CaptchaChallengeRepository captchaRepository;

	public CaptchaService(CaptchaChallengeRepository captchaRepository) {
		this.captchaRepository = captchaRepository;
	}

	@Transactional
	public CaptchaChallenge generate() {
		String answer = randomAnswer();
		String imageBase64 = renderImage(answer);
		CaptchaChallenge challenge = CaptchaChallenge.create(answer, imageBase64,
				OffsetDateTime.now().plusMinutes(EXPIRY_MINUTES));
		return captchaRepository.save(challenge);
	}

	@Transactional
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
		CaptchaChallenge challenge = captchaRepository.findById(id)
				.orElseThrow(() -> new AuthException(400, "captcha verification failed"));
		if (challenge.isUsed() || challenge.isExpired()) {
			throw new AuthException(400, "captcha expired");
		}
		if (!challenge.answer().equalsIgnoreCase(answer.trim())) {
			throw new AuthException(400, "captcha verification failed");
		}
		challenge.markUsed();
		captchaRepository.save(challenge);
	}

	private String randomAnswer() {
		StringBuilder sb = new StringBuilder(CAPTCHA_LENGTH);
		for (int i = 0; i < CAPTCHA_LENGTH; i++) {
			sb.append(CHARS.charAt(ThreadLocalRandom.current().nextInt(CHARS.length())));
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
