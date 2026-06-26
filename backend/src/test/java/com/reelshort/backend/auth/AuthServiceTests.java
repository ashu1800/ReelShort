package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@SpringBootTest
class AuthServiceTests {

	@Autowired
	private AuthService authService;

	@Autowired
	private PasswordHasher passwordHasher;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void registerStoresPasswordHashInsteadOfPlaintext() {
		AuthToken token = authService.register("frank", "Password123");

		UserAccount user = userAccountRepository.findById(token.userId()).orElseThrow();
		assertThat(user.passwordHash()).isNotEqualTo("Password123");
		assertThat(passwordHasher.matches("Password123", user.passwordHash())).isTrue();
		assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void registerRejectsDuplicateUsername() {
		authService.register("grace", "Password123");

		assertThatThrownBy(() -> authService.register("grace", "Password123"))
				.isInstanceOf(AuthException.class)
				.extracting("statusCode")
				.isEqualTo(409);
	}

	@Test
	void registerTrimsUsernameBeforeSaving() {
		AuthToken token = authService.register(" kim ", "Password123");

		assertThat(token.username()).isEqualTo("kim");
		assertThat(userAccountRepository.findByUsername("kim")).isPresent();
		assertThat(userAccountRepository.findByUsername(" kim ")).isEmpty();
	}

	@Test
	void loginTrimsUsernameBeforeLookup() {
		authService.register("lisa", "Password123");

		AuthToken token = authService.login(" lisa ", "Password123");

		assertThat(token.username()).isEqualTo("lisa");
	}

	@Test
	void loginRejectsMissingUserWithGenericMessage() {
		assertThatThrownBy(() -> authService.login("missing", "Password123"))
				.isInstanceOf(AuthException.class)
				.hasMessage("invalid username or password")
				.extracting("statusCode")
				.isEqualTo(401);
	}

	@Test
	void loginRejectsDisabledUserBeforePasswordCheck() {
		userAccountRepository.save(UserAccount.create("helen", passwordHasher.hash("Password123"), UserStatus.DISABLED));

		assertThatThrownBy(() -> authService.login("helen", "wrong"))
				.isInstanceOf(AuthException.class)
				.hasMessage("user disabled")
				.extracting("statusCode")
				.isEqualTo(403);
	}
}
