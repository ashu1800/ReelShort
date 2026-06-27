package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.reelshort.backend.auth.PasswordHasher;

@SpringBootTest
@TestPropertySource(properties = {
		"reelshort.admin.session.cleanup-retention=1d"
})
class AdminSessionCleanupServiceTests {

	@Autowired
	private AdminSessionCleanupService cleanupService;

	@Autowired
	private AdminTokenRepository adminTokenRepository;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private AdminRoleRepository adminRoleRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	@Test
	void deletesOnlyExpiredOrRevokedAdminTokensOlderThanRetention() {
		AdminRole superAdmin = adminRoleRepository.findByCode("SUPER_ADMIN").orElseThrow();
		AdminUser admin = AdminUser.create("admin-session-cleanup", passwordHasher.hash("OpsAdmin123"),
				AdminUserStatus.ACTIVE);
		admin.assignRole(superAdmin);
		adminUserRepository.saveAndFlush(admin);
		OffsetDateTime now = OffsetDateTime.parse("2026-06-27T12:00:00Z");
		AdminToken oldExpired = adminTokenRepository.save(AdminToken.issue(
				"admin-cleanup-old-expired", admin.id(), admin.username(), now.minusDays(3)));
		AdminToken recentExpired = adminTokenRepository.save(AdminToken.issue(
				"admin-cleanup-recent-expired", admin.id(), admin.username(), now.minusHours(12)));
		AdminToken oldRevoked = AdminToken.issue(
				"admin-cleanup-old-revoked", admin.id(), admin.username(), now.plusDays(1));
		oldRevoked.revoke(now.minusDays(2));
		oldRevoked = adminTokenRepository.save(oldRevoked);
		AdminToken recentRevoked = AdminToken.issue(
				"admin-cleanup-recent-revoked", admin.id(), admin.username(), now.plusDays(1));
		recentRevoked.revoke(now.minusHours(2));
		recentRevoked = adminTokenRepository.save(recentRevoked);
		AdminToken active = adminTokenRepository.save(AdminToken.issue(
				"admin-cleanup-active", admin.id(), admin.username(), now.plusDays(2)));

		int deleted = cleanupService.cleanup(now);

		assertThat(deleted).isEqualTo(2);
		assertThat(adminTokenRepository.findById(oldExpired.id())).isEmpty();
		assertThat(adminTokenRepository.findById(oldRevoked.id())).isEmpty();
		assertThat(adminTokenRepository.findById(recentExpired.id())).isPresent();
		assertThat(adminTokenRepository.findById(recentRevoked.id())).isPresent();
		assertThat(adminTokenRepository.findById(active.id())).isPresent();
	}
}
