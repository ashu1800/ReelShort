package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
		"reelshort.auth.session.cleanup-retention=1d"
})
class AuthSessionCleanupServiceTests {

	@Autowired
	private AuthSessionCleanupService cleanupService;

	@Autowired
	private AccessTokenRepository accessTokenRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void deletesOnlyExpiredOrRevokedTokensOlderThanRetention() {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("cleanup-user", "hash", UserStatus.ACTIVE));
		OffsetDateTime now = OffsetDateTime.parse("2026-06-27T12:00:00Z");
		AccessToken oldExpired = accessTokenRepository.save(AccessToken.issue(
				"cleanup-old-expired", user, now.minusDays(4), now.minusDays(3)));
		AccessToken recentExpired = accessTokenRepository.save(AccessToken.issue(
				"cleanup-recent-expired", user, now.minusDays(2), now.minusHours(12)));
		AccessToken oldRevoked = AccessToken.issue(
				"cleanup-old-revoked", user, now.minusDays(4), now.plusDays(1));
		oldRevoked.revoke(now.minusDays(2));
		oldRevoked = accessTokenRepository.save(oldRevoked);
		AccessToken recentRevoked = AccessToken.issue(
				"cleanup-recent-revoked", user, now.minusDays(1), now.plusDays(1));
		recentRevoked.revoke(now.minusHours(2));
		recentRevoked = accessTokenRepository.save(recentRevoked);
		AccessToken active = accessTokenRepository.save(AccessToken.issue(
				"cleanup-active", user, now.minusHours(1), now.plusDays(2)));

		int deleted = cleanupService.cleanup(now);

		assertThat(deleted).isEqualTo(2);
		assertThat(accessTokenRepository.findById(oldExpired.id())).isEmpty();
		assertThat(accessTokenRepository.findById(oldRevoked.id())).isEmpty();
		assertThat(accessTokenRepository.findById(recentExpired.id())).isPresent();
		assertThat(accessTokenRepository.findById(recentRevoked.id())).isPresent();
		assertThat(accessTokenRepository.findById(active.id())).isPresent();
	}
}
