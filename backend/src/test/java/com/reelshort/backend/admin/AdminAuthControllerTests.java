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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.auth.TokenHasher;

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
		MvcResult result = mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123"
						}
						""".formatted(username)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}
}
