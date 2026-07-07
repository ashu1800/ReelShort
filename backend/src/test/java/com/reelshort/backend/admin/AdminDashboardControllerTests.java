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
import com.reelshort.backend.TestAppUsers;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void dashboardSummaryRequiresAdminToken() throws Exception {
		mockMvc.perform(get("/api/admin/dashboard/summary"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void appTokenCannotAccessDashboardSummary() throws Exception {
		String appToken = registerAndExtractAppToken("dashboard-boundary-user");

		mockMvc.perform(get("/api/admin/dashboard/summary")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void adminCanReadDashboardSummary() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/dashboard/summary")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.users.total").isNumber())
				.andExpect(jsonPath("$.data.orders.total").isNumber())
				.andExpect(jsonPath("$.data.payments.total").isNumber())
				.andExpect(jsonPath("$.data.content.bookCount").isNumber())
				.andExpect(jsonPath("$.data.auditLogs.latest").isArray());
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
