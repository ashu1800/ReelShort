package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@SpringBootTest
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
	}
}
