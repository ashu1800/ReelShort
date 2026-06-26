package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.auth.AuthException;

@SpringBootTest
class AdminAuthServiceTests {

	@Autowired
	private AdminAuthService adminAuthService;

	@Test
	void loginRejectsUnknownAdminUsername() {
		assertThatThrownBy(() -> adminAuthService.login("unknown-admin", "Admin123"))
				.isInstanceOf(AuthException.class)
				.hasMessage("invalid username or password");
	}
}
