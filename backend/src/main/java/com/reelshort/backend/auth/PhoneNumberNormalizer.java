package com.reelshort.backend.auth;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {

	private static final Set<String> SUPPORTED_COUNTRY_CODES = Set.of(
			"+1", "+44", "+61", "+65", "+852", "+853", "+886", "+81", "+82", "+60");

	public PhoneIdentity normalize(String countryCode, String phoneNumber) {
		String normalizedCountryCode = normalizeCountryCode(countryCode);
		if (!SUPPORTED_COUNTRY_CODES.contains(normalizedCountryCode)) {
			throw new AuthException(400, "unsupported phone country code");
		}
		String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
		String e164 = normalizedCountryCode + normalizedPhoneNumber;
		if (e164.length() < 8 || e164.length() > 16) {
			throw new AuthException(400, "invalid phone number");
		}
		return new PhoneIdentity(normalizedCountryCode, normalizedPhoneNumber, e164);
	}

	public PhoneIdentity normalizeAccount(String account) {
		String trimmed = account == null ? "" : account.trim();
		if (!trimmed.startsWith("+")) {
			throw new AuthException(400, "invalid recipient account");
		}
		for (String countryCode : SUPPORTED_COUNTRY_CODES) {
			if (trimmed.startsWith(countryCode)) {
				return normalize(countryCode, trimmed.substring(countryCode.length()));
			}
		}
		throw new AuthException(400, "unsupported phone country code");
	}

	private String normalizeCountryCode(String countryCode) {
		String trimmed = countryCode == null ? "" : countryCode.trim();
		if (!trimmed.startsWith("+")) {
			trimmed = "+" + trimmed;
		}
		if ("+86".equals(trimmed)) {
			throw new AuthException(400, "unsupported phone country code");
		}
		return trimmed;
	}

	private String normalizePhoneNumber(String phoneNumber) {
		String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9]", "");
		if (digits.isBlank()) {
			throw new AuthException(400, "invalid phone number");
		}
		return digits;
	}
}
