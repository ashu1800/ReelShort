package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.TestAppUsers;

@SpringBootTest
class AuthServiceTests {

	@Autowired
	private AuthService authService;

	@Autowired
	private CaptchaService captchaService;

	@Autowired
	private PasswordHasher passwordHasher;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void registerStoresPasswordHashInsteadOfPlaintext() {
		AuthToken token = registerUser("auth-test-register", "Password123");

		UserAccount user = userAccountRepository.findById(token.userId()).orElseThrow();
		assertThat(user.username()).isEqualTo(token.username());
		assertThat(user.passwordHash()).isNotEqualTo("Password123");
		assertThat(passwordHasher.matches("Password123", user.passwordHash())).isTrue();
		assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void registerRejectsDuplicateUsername() {
		registerUser("auth-test-duplicate", "Password123");

		assertThatThrownBy(() -> registerUser("auth-test-duplicate", "Password123"))
				.isInstanceOf(AuthException.class)
				.extracting("statusCode")
				.isEqualTo(409);
	}

	@Test
	void loginRejectsMissingUserWithGenericMessage() {
		assertThatThrownBy(() -> authService.login("auth-test-missing", "Password123"))
				.isInstanceOf(AuthException.class)
				.hasMessage("invalid username or password")
				.extracting("statusCode")
				.isEqualTo(401);
	}

	@Test
	void loginRejectsDisabledUsersBeforePasswordCheck() {
		UserAccount disabled = userAccountRepository.save(UserAccount.create("auth-test-disabled",
				passwordHasher.hash("Password123"), UserStatus.DISABLED));

		assertThatThrownBy(() -> authService.login(disabled.username(), "wrong"))
				.isInstanceOf(AuthException.class)
				.hasMessage("user disabled")
				.extracting("statusCode")
				.isEqualTo(403);
	}

	private AuthToken registerUser(String usernameSeed, String password) {
		String username = TestAppUsers.usernameFor(usernameSeed);
		com.reelshort.backend.auth.CaptchaChallenge challenge = captchaService.generate();
		return authService.register(username, password, challenge.id().toString(), challenge.answer());
	}
}
