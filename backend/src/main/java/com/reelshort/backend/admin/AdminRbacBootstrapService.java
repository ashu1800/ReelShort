package com.reelshort.backend.admin;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRbacBootstrapService implements ApplicationRunner {

	private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

	private final AdminProperties adminProperties;
	private final AdminUserRepository adminUserRepository;
	private final AdminRoleRepository adminRoleRepository;
	private final AdminPermissionRepository adminPermissionRepository;

	public AdminRbacBootstrapService(AdminProperties adminProperties, AdminUserRepository adminUserRepository,
			AdminRoleRepository adminRoleRepository, AdminPermissionRepository adminPermissionRepository) {
		this.adminProperties = adminProperties;
		this.adminUserRepository = adminUserRepository;
		this.adminRoleRepository = adminRoleRepository;
		this.adminPermissionRepository = adminPermissionRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		bootstrap();
	}

	@Transactional
	public void bootstrap() {
		Map<String, AdminPermission> permissions = permissions();
		AdminRole superAdminRole = adminRoleRepository.findByCode(SUPER_ADMIN_ROLE)
				.orElseGet(() -> adminRoleRepository.save(AdminRole.create(SUPER_ADMIN_ROLE, "Super Admin")));
		permissions.values().forEach(superAdminRole::grant);
		adminRoleRepository.save(superAdminRole);

		AdminUser admin = adminUserRepository.findByUsername(adminProperties.username())
				.orElseGet(() -> adminUserRepository.save(AdminUser.create(adminProperties.username(),
						adminProperties.passwordHash(), AdminUserStatus.ACTIVE)));
		admin.assignRole(superAdminRole);
		adminUserRepository.save(admin);
	}

	private Map<String, AdminPermission> permissions() {
		Map<String, String> descriptions = permissionDescriptions();
		Map<String, AdminPermission> permissions = new LinkedHashMap<>();
		descriptions.forEach((code, description) -> permissions.put(code,
				adminPermissionRepository.findByCode(code)
						.orElseGet(() -> adminPermissionRepository.save(AdminPermission.create(code, description)))));
		return permissions;
	}

	private Map<String, String> permissionDescriptions() {
		Map<String, String> descriptions = new LinkedHashMap<>();
		descriptions.put(AdminPermissions.USER_READ, "Read app users and user activity");
		descriptions.put(AdminPermissions.USER_WRITE, "Change app user status");
		descriptions.put(AdminPermissions.POINTS_ADJUST, "Adjust app user points");
		descriptions.put(AdminPermissions.AUDIT_READ, "Read admin audit logs");
		descriptions.put(AdminPermissions.CONTENT_CACHE_READ, "Read content cache status");
		descriptions.put(AdminPermissions.CONTENT_CACHE_WRITE, "Refresh content cache");
		descriptions.put(AdminPermissions.SYSTEM_CONFIG_READ, "Read system configs");
		descriptions.put(AdminPermissions.SYSTEM_CONFIG_WRITE, "Update system configs");
		return descriptions;
	}
}

