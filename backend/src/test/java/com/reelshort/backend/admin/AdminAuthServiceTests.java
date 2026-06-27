package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

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
		assertThat(token.revokedAt()).isNull();
	}

	@Test
	void logoutRevokesExistingAdminToken() {
		AdminRole superAdmin = adminRoleRepository.findByCode("SUPER_ADMIN").orElseThrow();
		AdminUser admin = AdminUser.create("logout-admin", passwordHasher.hash("OpsAdmin123"), AdminUserStatus.ACTIVE);
		admin.assignRole(superAdmin);
		adminUserRepository.saveAndFlush(admin);
		AdminAuthTokenResponse response = adminAuthService.login("logout-admin", "OpsAdmin123");

		adminAuthService.logout(response.token());

		AdminToken token = adminTokenRepository.findByTokenHash(tokenHasher.hash(response.token())).orElseThrow();
		assertThat(token.revokedAt()).isNotNull();
		assertThat(token.isRevoked()).isTrue();
	}

	@Test
	void logoutMissingAdminTokenDoesNotThrowOrCreateRows() {
		long beforeCount = adminTokenRepository.count();

		adminAuthService.logout("missing-admin-token");

		assertThat(adminTokenRepository.count()).isEqualTo(beforeCount);
	}

	@Test
	void deletesExpiredOrRevokedAdminTokensBeforeCutoff() {
		AdminRole superAdmin = adminRoleRepository.findByCode("SUPER_ADMIN").orElseThrow();
		AdminUser admin = AdminUser.create("cleanup-admin", passwordHasher.hash("OpsAdmin123"), AdminUserStatus.ACTIVE);
		admin.assignRole(superAdmin);
		adminUserRepository.saveAndFlush(admin);
		AdminToken expired = adminTokenRepository.save(AdminToken.issue("admin-expired-token", admin.id(), admin.username(),
				OffsetDateTime.parse("2026-06-26T00:00:00Z")));
		AdminToken active = adminTokenRepository.save(AdminToken.issue("admin-active-token", admin.id(), admin.username(),
				OffsetDateTime.parse("2026-07-01T00:00:00Z")));
		AdminToken revoked = adminTokenRepository.save(AdminToken.issue("admin-revoked-token", admin.id(), admin.username(),
				OffsetDateTime.parse("2026-07-01T00:00:00Z")));
		revoked.revoke(OffsetDateTime.parse("2026-06-25T00:00:00Z"));
		adminTokenRepository.save(revoked);

		int deleted = adminTokenRepository.deleteExpiredOrRevokedBefore(OffsetDateTime.parse("2026-06-27T00:00:00Z"));

		assertThat(deleted).isEqualTo(2);
		assertThat(adminTokenRepository.findById(expired.id())).isEmpty();
		assertThat(adminTokenRepository.findById(revoked.id())).isEmpty();
		assertThat(adminTokenRepository.findById(active.id())).isPresent();
	}
}
