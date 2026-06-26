package com.reelshort.backend.auth;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.reelshort.backend.user.UserAccount;

@Component
public class OpaqueTokenService implements TokenService {

	private final SecureRandom secureRandom = new SecureRandom();
	private final AccessTokenRepository accessTokenRepository;
	private final TokenHasher tokenHasher;

	public OpaqueTokenService(AccessTokenRepository accessTokenRepository, TokenHasher tokenHasher) {
		this.accessTokenRepository = accessTokenRepository;
		this.tokenHasher = tokenHasher;
	}

	@Override
	public AuthToken issue(UserAccount user) {
		byte[] tokenBytes = new byte[32];
		secureRandom.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		accessTokenRepository.save(AccessToken.issue(tokenHasher.hash(token), user));
		return new AuthToken(user.id(), user.username(), token, "Bearer");
	}
}
