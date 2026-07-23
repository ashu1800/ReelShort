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
		assertThatThrownBy(() -> authService.login("auth-test-missing", "Password123", LoginSource.APP))
				.isInstanceOf(AuthException.class)
				.hasMessage("invalid username or password")
				.extracting("statusCode")
				.isEqualTo(401);
	}

	@Test
	void loginRejectsDisabledUsersBeforePasswordCheck() {
		UserAccount disabled = userAccountRepository.save(UserAccount.create("auth-test-disabled",
				passwordHasher.hash("Password123"), UserStatus.DISABLED));

		assertThatThrownBy(() -> authService.login(disabled.username(), "wrong", LoginSource.APP))
				.isInstanceOf(AuthException.class)
				.hasMessage("user disabled")
				.extracting("statusCode")
				.isEqualTo(403);
	}

	@Test
	void appLoginRecordsFirstAppLoginTimeOnce() {
		AuthToken token = registerUser("auth-test-app-login", "Password123");

		authService.login(token.username(), "Password123", LoginSource.APP);
		UserAccount first = userAccountRepository.findByUsername(token.username()).orElseThrow();
		assertThat(first.firstAppLoginAt()).isNotNull();

		authService.login(token.username(), "Password123", LoginSource.APP);
		UserAccount second = userAccountRepository.findByUsername(token.username()).orElseThrow();
		assertThat(second.firstAppLoginAt()).isEqualTo(first.firstAppLoginAt());
	}

	@Test
	void scriptLoginRequiresPriorAppLogin() {
		AuthToken token = registerUser("auth-test-script-blocked", "Password123");

		assertThatThrownBy(() -> authService.login(token.username(), "Password123", LoginSource.SCRIPT))
				.isInstanceOf(AuthException.class)
				.hasMessage("请先在 App 登录一次后再使用脚本登录")
				.extracting("statusCode")
				.isEqualTo(403);
	}

	@Test
	void scriptLoginSucceedsAfterAppLogin() {
		AuthToken token = registerUser("auth-test-script-after-app", "Password123");

		authService.login(token.username(), "Password123", LoginSource.APP);
		AuthToken scriptToken = authService.login(token.username(), "Password123", LoginSource.SCRIPT);

		assertThat(scriptToken.username()).isEqualTo(token.username());
		assertThat(scriptToken.token()).isNotBlank();
	}

	@Test
	void scriptLoginWithWrongPasswordDoesNotRevealAppLoginRequirement() {
		AuthToken token = registerUser("auth-test-script-wrong-password", "Password123");

		assertThatThrownBy(() -> authService.login(token.username(), "wrong", LoginSource.SCRIPT))
				.isInstanceOf(AuthException.class)
				.hasMessage("invalid username or password")
				.extracting("statusCode")
				.isEqualTo(401);
	}

	private AuthToken registerUser(String usernameSeed, String password) {
		String username = TestAppUsers.usernameFor(usernameSeed);
		com.reelshort.backend.auth.CaptchaChallenge challenge = captchaService.generate();
		return authService.register(username, password, challenge.id().toString(), challenge.answer());
	}
}
