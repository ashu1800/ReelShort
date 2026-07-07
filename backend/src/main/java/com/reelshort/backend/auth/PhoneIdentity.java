package com.reelshort.backend.auth;

public record PhoneIdentity(String countryCode, String phoneNumber, String e164) {
}
