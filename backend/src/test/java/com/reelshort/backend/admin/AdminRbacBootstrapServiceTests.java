package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class AdminRbacBootstrapServiceTests {

	@Autowired
	private AdminRbacBootstrapService bootstrapService;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private AdminRoleRepository adminRoleRepository;

	@Autowired
	private AdminPermissionRepository adminPermissionRepository;

	@Autowired
	private AdminAuthService adminAuthService;

	@Autowired
	private AdminProperties adminProperties;

	@Autowired
	private EntityManager entityManager;

	@Test
	void bootstrapsDefaultAdminWithSuperAdminRoleAndPermissions() {
		AdminUser admin = adminUserRepository.findByUsername("admin").orElseThrow();

		assertThat(admin.status()).isEqualTo(AdminUserStatus.ACTIVE);
		assertThat(admin.passwordHash()).isNotBlank();
		assertThat(admin.roles()).extracting(AdminRole::code).containsExactly("SUPER_ADMIN");
		assertThat(admin.permissionCodes()).containsAll(AdminPermissions.ALL);

		AdminRole role = adminRoleRepository.findByCode("SUPER_ADMIN").orElseThrow();
		assertThat(role.permissions()).extracting(AdminPermission::code).containsAll(AdminPermissions.ALL);
		assertThat(adminPermissionRepository.findAll()).hasSize(AdminPermissions.ALL.size());
	}

	@Test
	void bootstrapCanRunRepeatedlyWithoutDuplicatingRecords() {
		bootstrapService.bootstrap();
		entityManager.flush();
		entityManager.clear();
		bootstrapService.bootstrap();
		entityManager.flush();

		assertThat(adminUserRepository.count()).isEqualTo(1);
		assertThat(adminRoleRepository.count()).isEqualTo(1);
		Set<String> permissionCodes = adminPermissionRepository.findAll().stream()
				.map(AdminPermission::code)
				.collect(Collectors.toSet());
		assertThat(permissionCodes).isEqualTo(AdminPermissions.ALL);
		assertThat(joinCount("admin_user_roles")).isEqualTo(1);
		assertThat(joinCount("role_permissions")).isEqualTo(AdminPermissions.ALL.size());
	}

	@Test
	void bootstrapRotatesStoredPasswordHashFromConfiguration() {
		AdminUser admin = adminUserRepository.findByUsername("admin").orElseThrow();
		// 模拟"配置轮换前的库内哈希"：与当前配置不一致
		admin.changePasswordHash("$2b$12$oldhasholdhasholdhasholdhasholdhasholdhasholdhasholdhashol");
		adminUserRepository.saveAndFlush(admin);
		entityManager.clear();

		bootstrapService.bootstrap();
		entityManager.flush();
		entityManager.clear();

		// P2-13: 配置的哈希必须真正生效（而不是像修复前那样被静默忽略）
		assertThat(adminUserRepository.findByUsername("admin").orElseThrow().passwordHash())
				.isEqualTo(adminProperties.passwordHash());
		// 测试 profile 的管理员口令 Admin123 可用
		assertThat(adminAuthService.login(adminProperties.username(), "Admin123").token()).isNotBlank();
	}

	@Test
	void bootstrapDoesNotRestoreManuallyRevokedSuperAdminRole() {
		AdminUser admin = adminUserRepository.findByUsername("admin").orElseThrow();
		AdminRole superAdminRole = adminRoleRepository.findByCode("SUPER_ADMIN").orElseThrow();
		admin.revokeRole(superAdminRole);
		adminUserRepository.saveAndFlush(admin);
		entityManager.flush();
		entityManager.clear();

		bootstrapService.bootstrap();
		entityManager.flush();
		entityManager.clear();

		// P2-13: 重启不再静默还原被运维收回的权限
		assertThat(adminUserRepository.findByUsername("admin").orElseThrow().roles()).isEmpty();
		assertThat(joinCount("admin_user_roles")).isZero();
	}

	private long joinCount(String tableName) {
		return ((Number) entityManager.createNativeQuery("select count(*) from " + tableName)
				.getSingleResult()).longValue();
	}
}
