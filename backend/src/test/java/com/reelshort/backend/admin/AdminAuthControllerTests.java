package com.reelshort.backend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

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
