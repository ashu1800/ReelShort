package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PrivateKeyNormalizerTests {

	@Test
	void stripsLowercaseAndUppercaseHexPrefixes() {
		String key = "a".repeat(64);

		assertThat(PrivateKeyNormalizer.normalize("0x" + key)).isEqualTo(key);
		assertThat(PrivateKeyNormalizer.normalize("0X" + key)).isEqualTo(key);
		assertThat(PrivateKeyNormalizer.normalize(key)).isEqualTo(key);
	}
}
