package com.reelshort.backend.withdrawal;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

public final class TronAddress {

	private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

	private TronAddress() {
	}

	public static boolean isValid(String address) {
		if (address == null || address.length() != 34 || !address.startsWith("T")) {
			return false;
		}
		byte[] decoded = decode(address);
		if (decoded.length != 25 || decoded[0] != 0x41) {
			return false;
		}
		byte[] payload = Arrays.copyOf(decoded, 21);
		byte[] checksum = Arrays.copyOfRange(decoded, 21, 25);
		return Arrays.equals(checksum, Arrays.copyOf(doubleSha256(payload), 4));
	}

	private static byte[] decode(String value) {
		BigInteger number = BigInteger.ZERO;
		for (int index = 0; index < value.length(); index++) {
			int digit = ALPHABET.indexOf(value.charAt(index));
			if (digit < 0) {
				return new byte[0];
			}
			number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
		}
		byte[] raw = number.toByteArray();
		if (raw.length > 0 && raw[0] == 0) {
			raw = Arrays.copyOfRange(raw, 1, raw.length);
		}
		return raw;
	}

	private static byte[] doubleSha256(byte[] value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(digest.digest(value));
		}
		catch (Exception exception) {
			throw new IllegalStateException("SHA-256 not available", exception);
		}
	}
}
