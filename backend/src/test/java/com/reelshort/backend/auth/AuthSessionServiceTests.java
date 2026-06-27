package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@SpringBootTest
@TestPropertySource(properties = {
		"reelshort.auth.session.access-token-ttl=2h"
})
class AuthSessionServiceTests {

	@Autowired
	private TokenService tokenService;

	@Autowired
	private TokenHasher tokenHasher;

	@Autowired
	private AccessTokenRepository accessTokenRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void issueUsesConfiguredAccessTokenTtl() {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("session-ttl-user", "hash", UserStatus.ACTIVE));
		OffsetDateTime beforeIssue = OffsetDateTime.now().minusSeconds(1);

		AuthToken token = tokenService.issue(user);

		AccessToken storedToken = accessTokenRepository.findByTokenHash(tokenHasher.hash(token.token())).orElseThrow();
		assertThat(Duration.between(storedToken.issuedAt(), storedToken.expiresAt())).isEqualTo(Duration.ofHours(2));
		assertThat(storedToken.issuedAt()).isAfter(beforeIssue);
	}

	@Test
	void revokeMarksExistingTokenRevoked() {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("session-revoke-user", "hash", UserStatus.ACTIVE));
		AuthToken token = tokenService.issue(user);

		tokenService.revoke(token.token());

		AccessToken storedToken = accessTokenRepository.findByTokenHash(tokenHasher.hash(token.token())).orElseThrow();
		assertThat(storedToken.revokedAt()).isNotNull();
		assertThat(storedToken.isRevoked()).isTrue();
	}

	@Test
	void revokeMissingTokenDoesNotThrowOrCreateRows() {
		long beforeCount = accessTokenRepository.count();

		tokenService.revoke("missing-token");

		assertThat(accessTokenRepository.count()).isEqualTo(beforeCount);
	}
}
