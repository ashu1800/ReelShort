package com.reelshort.backend.system.security;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;

import org.springframework.stereotype.Service;

/**
 * RFC 6238 TOTP (Time-based One-Time Password) implementation using only JDK primitives.
 * Used for two-factor authentication on sensitive operations like withdrawal batch payout.
 */
@Service
public class TotpService {

	private static final int TIME_STEP_SECONDS = 30;
	private static final int CODE_DIGITS = 6;
	private static final int SECRET_BYTES = 20;
	private static final int VERIFY_WINDOW = 1; // allow ±30s drift

	private static final Base32 BASE32 = new Base32();

	/**
	 * Generate a new random 20-byte secret, Base32-encoded (no padding) for QR code registration.
	 */
	public String generateSecret() {
		byte[] bytes = new byte[SECRET_BYTES];
		new SecureRandom().nextBytes(bytes);
		return BASE32.encodeAsString(bytes).replace("=", "");
	}

	/**
	 * Verify a user-supplied 6-digit code against the secret, allowing ±30s clock drift.
	 */
	public boolean verify(String secret, String code) {
		if (secret == null || secret.isBlank() || code == null || code.length() != CODE_DIGITS) {
			return false;
		}
		long currentStep = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
		for (int offset = -VERIFY_WINDOW; offset <= VERIFY_WINDOW; offset++) {
			String expected = generateCode(secret, currentStep + offset);
			if (constantTimeEquals(expected, code)) {
				return true;
			}
		}
		return false;
	}

	private String generateCode(String secret, long timeStep) {
		byte[] key = BASE32.decode(secret);
		byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, "HmacSHA1"));
			byte[] hash = mac.doFinal(timeBytes);
			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24)
					| ((hash[offset + 1] & 0xFF) << 16)
					| ((hash[offset + 2] & 0xFF) << 8)
					| (hash[offset + 3] & 0xFF);
			int code = binary % (int) Math.pow(10, CODE_DIGITS);
			return String.format("%0" + CODE_DIGITS + "d", code);
		}
		catch (Exception exception) {
			throw new IllegalStateException("HMAC-SHA1 not available", exception);
		}
	}

	private boolean constantTimeEquals(String a, String b) {
		if (a.length() != b.length()) {
			return false;
		}
		int result = 0;
		for (int index = 0; index < a.length(); index++) {
			result |= a.charAt(index) ^ b.charAt(index);
		}
		return result == 0;
	}
}
