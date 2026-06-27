package com.reelshort.backend.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.admin.AdminPermission;
import com.reelshort.backend.admin.AdminPermissionRepository;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.AdminRole;
import com.reelshort.backend.admin.AdminRoleRepository;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.admin.AdminUserStatus;
import com.reelshort.backend.auth.PasswordHasher;

@SpringBootTest
@AutoConfigureMockMvc
class SystemLogControllerTests {

	private static Path logRoot;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AdminPermissionRepository adminPermissionRepository;

	@Autowired
	private AdminRoleRepository adminRoleRepository;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	@DynamicPropertySource
	static void configureLogRoot(DynamicPropertyRegistry registry) throws Exception {
		logRoot = Files.createTempDirectory("system-log-controller-tests");
		registry.add("reelshort.system.logs.root", () -> logRoot.toString());
		registry.add("reelshort.system.logs.max-lines", () -> "5");
	}

	@Test
	void systemLogsRequireAdminToken() throws Exception {
		mockMvc.perform(get("/api/admin/system/logs"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void adminCanReadSystemLogs() throws Exception {
		Files.write(logRoot.resolve("backend.log"), List.of("line-1", "line-2", "line-3"));
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/system/logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("file", "backend.log")
				.param("lines", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.files[0]").value("backend.log"))
				.andExpect(jsonPath("$.data.selectedFile").value("backend.log"))
				.andExpect(jsonPath("$.data.requestedLines").value(2))
				.andExpect(jsonPath("$.data.lines[0]").value("line-2"))
				.andExpect(jsonPath("$.data.lines[1]").value("line-3"));
	}

	@Test
	void unsafeLogFileNameReturnsBadRequest() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/system/logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("file", "../backend.log"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void adminWithoutSystemLogPermissionIsForbidden() throws Exception {
		String adminToken = createLimitedAdminAndLogin();

		mockMvc.perform(get("/api/admin/system/logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403))
				.andExpect(jsonPath("$.message").value("forbidden"));
	}

	@Test
	void defaultAdminIncludesSystemLogReadPermission() {
		org.assertj.core.api.Assertions.assertThat(AdminPermissions.ALL).contains("SYSTEM_LOG_READ");
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
		AdminRole role = AdminRole.create("SYSTEM_LOG_LIMITED_READER", "System Log Limited Reader");
		role.grant(userRead);
		adminRoleRepository.saveAndFlush(role);

		AdminUser admin = AdminUser.create("system-log-limited-admin", passwordHasher.hash("LimitedAdmin123"),
				AdminUserStatus.ACTIVE);
		admin.assignRole(role);
		adminUserRepository.saveAndFlush(admin);

		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "system-log-limited-admin",
						  "password": "LimitedAdmin123"
						}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}
}
