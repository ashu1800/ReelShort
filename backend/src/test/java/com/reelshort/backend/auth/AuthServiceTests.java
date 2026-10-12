package com.reelshort.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.system.security.CredentialAttemptRepository;
import com.reelshort.backend.system.security.CredentialLockedException;
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

	@Autowired
	private CredentialAttemptRepository credentialAttemptRepository;

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
	void loginRejectsDisabledUserOnlyAfterPasswordMatches() {
		UserAccount disabled = userAccountRepository.save(UserAccount.create("auth-test-disabled-ok",
				passwordHasher.hash("Password123"), UserStatus.DISABLED));

		// P2-14: 密码正确 + 账号被禁用 → 403 user disabled
		assertThatThrownBy(() -> authService.login(disabled.username(), "Password123"))
				.isInstanceOf(AuthException.class)
				.hasMessage("user disabled")
				.extracting("statusCode")
				.isEqualTo(403);
	}

	@Test
	void loginDoesNotRevealDisabledAccountWhenPasswordIsWrong() {
		UserAccount disabled = userAccountRepository.save(UserAccount.create("auth-test-disabled-wrong",
				passwordHasher.hash("Password123"), UserStatus.DISABLED));

		// P2-14: 密码错误时文案与状态码必须与"用户不存在"完全一致，避免枚举被禁用账号
		assertThatThrownBy(() -> authService.login(disabled.username(), "wrong"))
				.isInstanceOf(AuthException.class)
				.hasMessage("invalid username or password")
				.extracting("statusCode")
				.isEqualTo(401);
	}

	@Test
	void changePasswordLocksAfterRepeatedWrongOldPasswordsAndThenRejectsCorrectPassword() {
		AuthToken token = registerUser("auth-test-change-lock", "Password123");
		CurrentUser currentUser = new CurrentUser(token.userId(), token.username());
		for (int attempt = 0; attempt < 5; attempt++) {
			assertThatThrownBy(() -> authService.changePassword(currentUser, "wrong", "Password456"))
					.isInstanceOf(AuthException.class)
					.hasMessage("invalid old password")
					.extracting("statusCode")
					.isEqualTo(401);
		}

		// 锁定期内即使旧密码正确也拒绝（锁定判断优先于 BCrypt 比对）
		assertThatThrownBy(() -> authService.changePassword(currentUser, "Password123", "Password456"))
				.isInstanceOf(CredentialLockedException.class)
				.hasMessage("too many attempts, try again later");
		// 密码未被修改
		assertThat(passwordHasher.matches("Password123",
				userAccountRepository.findById(token.userId()).orElseThrow().passwordHash())).isTrue();
	}

	@Test
	void changePasswordClearsFailureCounterOnSuccess() {
		AuthToken token = registerUser("auth-test-change-reset", "Password123");
		CurrentUser currentUser = new CurrentUser(token.userId(), token.username());

		assertThatThrownBy(() -> authService.changePassword(currentUser, "wrong", "Password456"))
				.isInstanceOf(AuthException.class);
		assertThat(credentialAttemptRepository.findById(token.userId())).isPresent();

		authService.changePassword(currentUser, "Password123", "Password456");

		assertThat(credentialAttemptRepository.findById(token.userId())).isEmpty();
		assertThat(passwordHasher.matches("Password456",
				userAccountRepository.findById(token.userId()).orElseThrow().passwordHash())).isTrue();
	}

	private AuthToken registerUser(String usernameSeed, String password) {
		String username = TestAppUsers.usernameFor(usernameSeed);
		CaptchaService.GeneratedCaptcha challenge = captchaService.generate();
		return authService.register(username, password, challenge.captchaId(), challenge.answer());
	}
}
