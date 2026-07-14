package com.reelshort.backend.auth;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Base64;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.user.UserAccount;

@Component
public class OpaqueTokenService implements TokenService {

	private final SecureRandom secureRandom = new SecureRandom();
	private final AccessTokenRepository accessTokenRepository;
	private final TokenHasher tokenHasher;
	private final AuthSessionProperties authSessionProperties;

	public OpaqueTokenService(AccessTokenRepository accessTokenRepository, TokenHasher tokenHasher,
			AuthSessionProperties authSessionProperties) {
		this.accessTokenRepository = accessTokenRepository;
		this.tokenHasher = tokenHasher;
		this.authSessionProperties = authSessionProperties;
	}

	@Override
	@Transactional
	public AuthToken issue(UserAccount user) {
		byte[] tokenBytes = new byte[32];
		secureRandom.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		OffsetDateTime issuedAt = OffsetDateTime.now();
		OffsetDateTime expiresAt = issuedAt.plus(authSessionProperties.getAccessTokenTtl());
		accessTokenRepository.save(AccessToken.issue(tokenHasher.hash(token), user, issuedAt, expiresAt));
		return new AuthToken(user.id(), user.username(), token, "Bearer");
	}

	@Override
	@Transactional
	public void revoke(String token) {
		accessTokenRepository.findByTokenHash(tokenHasher.hash(token))
				.ifPresent(accessToken -> {
					if (!accessToken.isRevoked()) {
						accessToken.revoke(OffsetDateTime.now());
						accessTokenRepository.save(accessToken);
					}
				});
	}

	@Override
	@Transactional
	public void revokeAllForUser(UUID userId) {
		accessTokenRepository.revokeAllActiveByUserId(userId, OffsetDateTime.now());
	}
}
