package com.reelshort.backend.admin;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRbacBootstrapService implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminRbacBootstrapService.class);

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

		AdminUser admin = adminUserRepository.findByUsername(adminProperties.username()).orElse(null);
		if (admin != null) {
			// P2-13: 账号已存在时不同步角色，避免每次重启把运维手动收回的 SUPER_ADMIN 权限静默还原；
			// 但配置的密码哈希必须真正生效，否则运维轮换 REELSHORT_ADMIN_PASSWORD_HASH 是"假成功"。
			rotatePasswordHashIfConfigured(admin);
			return;
		}
		AdminUser createdAdmin = adminUserRepository.save(AdminUser.create(adminProperties.username(),
				adminProperties.passwordHash(), AdminUserStatus.ACTIVE));
		// P2-13: 只在新建账号时授予 SUPER_ADMIN
		createdAdmin.assignRole(superAdminRole);
		adminUserRepository.save(createdAdmin);
	}

	/**
	 * P2-13: 库中密码哈希与配置不一致时同步为配置值（并记录 INFO 事实）。
	 * 配置为空视为"未提供"，不做覆盖（生产启动的 fail-closed 校验已保证不会走到这里）。
	 */
	private void rotatePasswordHashIfConfigured(AdminUser admin) {
		String configuredHash = adminProperties.passwordHash();
		if (configuredHash == null || configuredHash.isBlank() || configuredHash.equals(admin.passwordHash())) {
			return;
		}
		admin.changePasswordHash(configuredHash);
		adminUserRepository.save(admin);
		log.info("admin password hash rotated from configuration. username={}", admin.username());
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
		descriptions.put(AdminPermissions.DASHBOARD_READ, "Read admin dashboard summary");
		descriptions.put(AdminPermissions.USER_READ, "Read app users and user activity");
		descriptions.put(AdminPermissions.USER_WRITE, "Change app user status");
		descriptions.put(AdminPermissions.POINTS_ADJUST, "Adjust app user points");
		descriptions.put(AdminPermissions.AUDIT_READ, "Read admin audit logs");
		descriptions.put(AdminPermissions.CONTENT_CACHE_READ, "Read content cache status");
		descriptions.put(AdminPermissions.CONTENT_CACHE_WRITE, "Refresh content cache");
		descriptions.put(AdminPermissions.SYSTEM_CONFIG_READ, "Read system configs");
		descriptions.put(AdminPermissions.SYSTEM_CONFIG_WRITE, "Update system configs");
		descriptions.put(AdminPermissions.SYSTEM_RUNTIME_READ, "Read system runtime diagnostics");
		descriptions.put(AdminPermissions.SYSTEM_LOG_READ, "Read system application logs");
		descriptions.put(AdminPermissions.SYSTEM_ALERT_READ, "Read system alerts");
		descriptions.put(AdminPermissions.SYSTEM_ALERT_WRITE, "Acknowledge system alerts");
		descriptions.put(AdminPermissions.ORDER_READ, "Read recharge orders");
		descriptions.put(AdminPermissions.ORDER_WRITE, "Confirm or reject VIP orders");
		descriptions.put(AdminPermissions.PAYMENT_EVENT_READ, "Read payment callback events");
		descriptions.put(AdminPermissions.WITHDRAWAL_READ, "Read withdrawal requests");
		descriptions.put(AdminPermissions.WITHDRAWAL_WRITE, "Approve or reject withdrawal requests");
		return descriptions;
	}
}
