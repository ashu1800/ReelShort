package com.reelshort.backend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.auth.TokenHasher;
import com.reelshort.backend.auth.PasswordHasher;
import com.reelshort.backend.system.security.TotpService;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AdminTokenRepository adminTokenRepository;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private TokenHasher tokenHasher;

	@Autowired
	private PasswordHasher passwordHasher;

	@Autowired
	private TotpService totpService;

	@Test
	void adminLoginReturnsAdminToken() throws Exception {
		mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "admin",
						  "password": "Admin123"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.token").isString());
	}

	@Test
	void adminLoginRejectsInvalidPassword() throws Exception {
		mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "admin",
						  "password": "Wrong123"
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	@Transactional
	void totpEnabledAdminMustProvideValidCodeAtLogin() throws Exception {
		String username = "totp-login-" + java.util.UUID.randomUUID();
		AdminUser admin = AdminUser.create(username, passwordHasher.hash("TotpAdmin123"), AdminUserStatus.ACTIVE);
		admin.enableTotp("JBSWY3DPEHPK3PXP");
		adminUserRepository.save(admin);

		mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"%s\",\"password\":\"TotpAdmin123\"}".formatted(username)))
				.andExpect(status().isUnauthorized());

		String code = totpService.generateCurrentCode(admin.totpSecret());
		mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"%s\",\"password\":\"TotpAdmin123\",\"totpCode\":\"%s\"}"
						.formatted(username, code)))
				.andExpect(status().isOk());
	}

	@Test
	void adminUsersRequiresAdminToken() throws Exception {
		mockMvc.perform(get("/api/admin/users"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void adminLogoutRequiresAdminToken() throws Exception {
		mockMvc.perform(post("/api/admin/auth/logout"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void adminLogoutRevokesCurrentToken() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(post("/api/admin/auth/logout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data").value("logged out"));

		mockMvc.perform(get("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("token revoked"));
	}

	@Test
	void revokedAdminTokenIsRejected() throws Exception {
		String adminToken = adminLogin();
		AdminToken storedToken = adminTokenRepository.findByTokenHash(tokenHasher.hash(adminToken)).orElseThrow();
		storedToken.revoke(OffsetDateTime.now().minusMinutes(1));
		adminTokenRepository.save(storedToken);

		mockMvc.perform(get("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("token revoked"));
	}

	@Test
	void expiredAdminTokenIsRejectedWithSpecificMessage() throws Exception {
		AdminUser admin = adminUserRepository.findByUsername("admin").orElseThrow();
		String rawToken = "expired-admin-token";
		adminTokenRepository.save(AdminToken.issue(
				tokenHasher.hash(rawToken),
				admin.id(),
				admin.username(),
				OffsetDateTime.now().minusHours(1)));

		mockMvc.perform(get("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("token expired"));
	}

	@Test
	void appTokenCannotAccessAdminUsers() throws Exception {
		String appToken = registerAndExtractAppToken("admin-boundary-user");

		mockMvc.perform(get("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void appTokenCannotAccessAdminAuditLogs() throws Exception {
		String appToken = registerAndExtractAppToken("admin-audit-boundary-user");

		mockMvc.perform(get("/api/admin/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void adminTokenCannotAccessAppApi() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
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

	private String registerAndExtractAppToken(String username) throws Exception {
		return TestAppUsers.token(mockMvc, objectMapper, username);
	}
}
