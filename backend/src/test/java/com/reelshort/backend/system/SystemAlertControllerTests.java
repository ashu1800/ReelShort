package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.admin.AdminAuditLogRepository;
import com.reelshort.backend.admin.AdminPermission;
import com.reelshort.backend.admin.AdminPermissionRepository;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.AdminRole;
import com.reelshort.backend.admin.AdminRoleRepository;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.admin.AdminUserStatus;
import com.reelshort.backend.auth.PasswordHasher;
import com.reelshort.backend.system.alerts.SystemAlert;
import com.reelshort.backend.system.alerts.SystemAlertRepository;
import com.reelshort.backend.system.alerts.SystemAlertSeverity;

@SpringBootTest
@AutoConfigureMockMvc
class SystemAlertControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SystemAlertRepository systemAlertRepository;

	@Autowired
	private AdminAuditLogRepository adminAuditLogRepository;

	@Autowired
	private AdminPermissionRepository adminPermissionRepository;

	@Autowired
	private AdminRoleRepository adminRoleRepository;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	@MockitoBean
	private Clock clock;

	@Test
	void alertsRequireAdminToken() throws Exception {
		mockMvc.perform(get("/api/admin/system/alerts"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void adminCanReadAlerts() throws Exception {
		configureClock("2026-06-27T11:00:00Z");
		systemAlertRepository.save(SystemAlert.open("runtime:dependency:redis", SystemAlertSeverity.WARNING,
				"Runtime dependency down: redis", "unavailable", Instant.parse("2026-06-27T10:00:00Z")));
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/system/alerts")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("status", "OPEN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data[?(@.alertKey == 'runtime:dependency:redis')].status").value("OPEN"));
	}

	@Test
	void adminWithoutAlertReadPermissionIsForbidden() throws Exception {
		String adminToken = createLimitedAdminAndLogin();

		mockMvc.perform(get("/api/admin/system/alerts")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403));
	}

	@Test
	void adminCanAcknowledgeAlertAndAuditAction() throws Exception {
		configureClock("2026-06-27T11:05:00Z");
		SystemAlert alert = systemAlertRepository.save(SystemAlert.open("runtime:dependency:database",
				SystemAlertSeverity.CRITICAL, "Runtime dependency down: database", "unavailable",
				Instant.parse("2026-06-27T10:00:00Z")));
		String adminToken = adminLogin();

		mockMvc.perform(post("/api/admin/system/alerts/{alertId}/acknowledge", alert.id())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
				.andExpect(jsonPath("$.data.acknowledgedBy").value("admin"));

		assertThat(adminAuditLogRepository.findAllByOrderByCreatedAtDesc())
				.anySatisfy(log -> {
					assertThat(log.action()).isEqualTo("SYSTEM_ALERT_ACKNOWLEDGED");
					assertThat(log.targetId()).isEqualTo(alert.id());
				});
	}

	@Test
	void defaultAdminIncludesAlertPermissions() {
		assertThat(AdminPermissions.ALL).contains("SYSTEM_ALERT_READ", "SYSTEM_ALERT_WRITE");
	}

	private void configureClock(String instant) {
		org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse(instant));
		org.mockito.Mockito.when(clock.getZone()).thenReturn(ZoneOffset.UTC);
	}

	private String adminLogin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "admin",
						  "password": "Admin123"
						}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}

	private String createLimitedAdminAndLogin() throws Exception {
		AdminPermission userRead = adminPermissionRepository.findByCode(AdminPermissions.USER_READ).orElseThrow();
		AdminRole role = AdminRole.create("ALERT_LIMITED_READER-" + UUID.randomUUID(), "Alert Limited Reader");
		role.grant(userRead);
		adminRoleRepository.saveAndFlush(role);

		String username = "alert-limited-admin-" + UUID.randomUUID();
		AdminUser admin = AdminUser.create(username, passwordHasher.hash("LimitedAdmin123"), AdminUserStatus.ACTIVE);
		admin.assignRole(role);
		adminUserRepository.saveAndFlush(admin);

		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "LimitedAdmin123"
						}
						""".formatted(username)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}
}
