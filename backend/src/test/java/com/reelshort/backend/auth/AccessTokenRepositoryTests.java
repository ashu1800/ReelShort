package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@SpringBootTest
@Transactional
class AccessTokenRepositoryTests {

	@Autowired
	private TokenService tokenService;

	@Autowired
	private TokenHasher tokenHasher;

	@Autowired
	private AccessTokenRepository accessTokenRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void issuedTokenStoresOnlyHashAndCanBeFoundByHash() {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("token-user", "hash", UserStatus.ACTIVE));

		AuthToken authToken = tokenService.issue(user);
		String tokenHash = tokenHasher.hash(authToken.token());

		AccessToken storedToken = accessTokenRepository.findByTokenHash(tokenHash).orElseThrow();
		assertThat(storedToken.tokenHash()).isEqualTo(tokenHash);
		assertThat(storedToken.tokenHash()).isNotEqualTo(authToken.token());
		assertThat(storedToken.user().id()).isEqualTo(user.id());
		assertThat(storedToken.expiresAt()).isAfter(storedToken.issuedAt());
		assertThat(storedToken.revokedAt()).isNull();
	}

	@Test
	void revokedTokenPersistsRevokedAt() {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("revoked-token-user", "hash", UserStatus.ACTIVE));
		AccessToken token = accessTokenRepository.save(AccessToken.issue("revoked-token-hash", user,
				OffsetDateTime.parse("2026-06-27T00:00:00Z"),
				OffsetDateTime.parse("2026-07-04T00:00:00Z")));

		token.revoke(OffsetDateTime.parse("2026-06-28T00:00:00Z"));
		AccessToken storedToken = accessTokenRepository.save(token);

		assertThat(storedToken.revokedAt()).isEqualTo(OffsetDateTime.parse("2026-06-28T00:00:00Z"));
		assertThat(storedToken.isRevoked()).isTrue();
	}

	@Test
	void deletesExpiredOrRevokedTokensBeforeCutoff() {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("cleanup-token-user", "hash", UserStatus.ACTIVE));
		AccessToken expired = accessTokenRepository.save(AccessToken.issue("expired-token-hash", user,
				OffsetDateTime.parse("2026-06-01T00:00:00Z"),
				OffsetDateTime.parse("2026-06-02T00:00:00Z")));
		AccessToken active = accessTokenRepository.save(AccessToken.issue("active-token-hash", user,
				OffsetDateTime.parse("2026-06-27T00:00:00Z"),
				OffsetDateTime.parse("2026-07-04T00:00:00Z")));
		AccessToken revoked = accessTokenRepository.save(AccessToken.issue("cleanup-revoked-token-hash", user,
				OffsetDateTime.parse("2026-06-20T00:00:00Z"),
				OffsetDateTime.parse("2026-07-01T00:00:00Z")));
		revoked.revoke(OffsetDateTime.parse("2026-06-21T00:00:00Z"));
		accessTokenRepository.save(revoked);

		int deleted = accessTokenRepository.deleteExpiredOrRevokedBefore(OffsetDateTime.parse("2026-06-26T00:00:00Z"));

		assertThat(deleted).isEqualTo(2);
		assertThat(accessTokenRepository.findById(expired.id())).isEmpty();
		assertThat(accessTokenRepository.findById(revoked.id())).isEmpty();
		assertThat(accessTokenRepository.findById(active.id())).isPresent();
	}
}
