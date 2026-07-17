package com.reelshort.backend.withdrawal;

final class PrivateKeyNormalizer {

	private PrivateKeyNormalizer() {
	}

	static String normalize(String privateKey) {
		if (privateKey == null || privateKey.length() < 2) {
			return privateKey;
		}
		return privateKey.charAt(0) == '0' && (privateKey.charAt(1) == 'x' || privateKey.charAt(1) == 'X')
				? privateKey.substring(2)
				: privateKey;
	}
}
