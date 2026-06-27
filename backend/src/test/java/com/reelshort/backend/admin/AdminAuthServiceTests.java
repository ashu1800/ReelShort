package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.auth.PasswordHasher;
import com.reelshort.backend.auth.TokenHasher;

@SpringBootTest
@Transactional
class AdminAuthServiceTests {

	@Autowired
	private AdminAuthService adminAuthService;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private AdminRoleRepository adminRoleRepository;

	@Autowired
	private AdminTokenRepository adminTokenRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	@Autowired
	private TokenHasher tokenHasher;

	@Test
	void loginRejectsUnknownAdminUsername() {
		assertThatThrownBy(() -> adminAuthService.login("unknown-admin", "Admin123"))
				.isInstanceOf(AuthException.class)
				.hasMessage("invalid username or password");
	}

	@Test
	void loginUsesPersistedAdminAccountAndStoresAdminUserIdOnToken() {
		AdminRole superAdmin = adminRoleRepository.findByCode("SUPER_ADMIN").orElseThrow();
		AdminUser admin = AdminUser.create("ops-admin", passwordHasher.hash("OpsAdmin123"), AdminUserStatus.ACTIVE);
		admin.assignRole(superAdmin);
		adminUserRepository.saveAndFlush(admin);

		AdminAuthTokenResponse response = adminAuthService.login("ops-admin", "OpsAdmin123");

		AdminToken token = adminTokenRepository.findByTokenHash(tokenHasher.hash(response.token())).orElseThrow();
		assertThat(response.username()).isEqualTo("ops-admin");
		assertThat(token.adminUserId()).isEqualTo(admin.id());
		assertThat(token.username()).isEqualTo("ops-admin");
	}
}
