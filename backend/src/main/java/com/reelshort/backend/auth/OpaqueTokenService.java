package com.reelshort.backend.auth;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.reelshort.backend.user.UserAccount;

@Component
public class OpaqueTokenService implements TokenService {

	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public AuthToken issue(UserAccount user) {
		byte[] tokenBytes = new byte[32];
		secureRandom.nextBytes(tokenBytes);
		return new AuthToken(user.id(), user.username(), Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes),
				"Bearer");
	}
}
