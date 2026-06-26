package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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
		bootstrapService.bootstrap();

		assertThat(adminUserRepository.count()).isEqualTo(1);
		assertThat(adminRoleRepository.count()).isEqualTo(1);
		Set<String> permissionCodes = adminPermissionRepository.findAll().stream()
				.map(AdminPermission::code)
				.collect(Collectors.toSet());
		assertThat(permissionCodes).isEqualTo(AdminPermissions.ALL);
	}
}
