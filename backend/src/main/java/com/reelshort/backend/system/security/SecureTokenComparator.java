package com.reelshort.backend.system.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * M9: 恒定时间字符串比较，防止时序侧信道攻击。
 * 用于内部 super-token、支付回调密钥等敏感字符串的比较。
 */
public final class SecureTokenComparator {

	private SecureTokenComparator() {
	}

	/**
	 * 恒定时间比较两个字符串是否相等。
	 * 使用 {@link MessageDigest#isEqual} 底层实现，避免短路逐字符比较的时序泄露。
	 */
	public static boolean equals(String expected, String provided) {
		if (expected == null || provided == null) {
			return false;
		}
		byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
		byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expectedBytes, providedBytes);
	}
}
