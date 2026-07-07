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
	void internalPhoneRegistrationStoresPasswordHashInsteadOfPlaintext() {
		AuthToken token = authService.internalRegisterPhone("+1", "4155550301", "Password123");

		UserAccount user = userAccountRepository.findById(token.userId()).orElseThrow();
		assertThat(user.username()).isEqualTo("+14155550301");
		assertThat(user.phoneE164()).isEqualTo("+14155550301");
		assertThat(user.passwordHash()).isNotEqualTo("Password123");
		assertThat(passwordHasher.matches("Password123", user.passwordHash())).isTrue();
		assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	void internalPhoneRegistrationRejectsDuplicatePhone() {
		authService.internalRegisterPhone("+1", "4155550302", "Password123");

		assertThatThrownBy(() -> authService.internalRegisterPhone("+1", "4155550302", "Password123"))
				.isInstanceOf(AuthException.class)
				.extracting("statusCode")
				.isEqualTo(409);
	}

	@Test
	void loginNormalizesPhoneBeforeLookup() {
		authService.internalRegisterPhone("+1", "(415) 555-0303", "Password123");

		AuthToken token = authService.login("1", "415-555-0303", "Password123");

		assertThat(token.username()).isEqualTo("+14155550303");
	}

	@Test
	void loginRejectsMissingPhoneWithGenericMessage() {
		assertThatThrownBy(() -> authService.login("+1", "4155550399", "Password123"))
				.isInstanceOf(AuthException.class)
				.hasMessage("invalid phone or password")
				.extracting("statusCode")
				.isEqualTo(401);
	}

	@Test
	void loginRejectsDisabledAndBlacklistedUsersBeforePasswordCheck() {
		userAccountRepository.save(UserAccount.createPhoneAccount("+1", "4155550304", "+14155550304",
				passwordHasher.hash("Password123")));
		UserAccount disabled = userAccountRepository.findByPhoneE164("+14155550304").orElseThrow();
		disabled.changeStatus(UserStatus.DISABLED);
		userAccountRepository.save(disabled);
		userAccountRepository.save(UserAccount.createPhoneAccount("+1", "4155550305", "+14155550305",
				passwordHasher.hash("Password123")));
		UserAccount blacklisted = userAccountRepository.findByPhoneE164("+14155550305").orElseThrow();
		blacklisted.changeStatus(UserStatus.BLACKLISTED);
		userAccountRepository.save(blacklisted);

		assertThatThrownBy(() -> authService.login("+1", "4155550304", "wrong"))
				.isInstanceOf(AuthException.class)
				.hasMessage("user disabled")
				.extracting("statusCode")
				.isEqualTo(403);
		assertThatThrownBy(() -> authService.login("+1", "4155550305", "wrong"))
				.isInstanceOf(AuthException.class)
				.hasMessage("user disabled")
				.extracting("statusCode")
				.isEqualTo(403);
	}
}
