package com.reelshort.backend.admin;

import static org.hamcrest.Matchers.nullValue;
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
class AdminStatisticsControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void statisticsRequiresAdminToken() throws Exception {
		mockMvc.perform(get("/api/admin/statistics"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void appTokenCannotAccessStatistics() throws Exception {
		String appToken = TestAppUsers.token(mockMvc, objectMapper, "statistics-boundary-user");

		mockMvc.perform(get("/api/admin/statistics")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void adminCanReadStatisticsWithAllFields() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/statistics")
				.param("range", "7d")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.range").value("7d"))
			.andExpect(jsonPath("$.data.since").isString())
			.andExpect(jsonPath("$.data.app.plays").isNumber())
			.andExpect(jsonPath("$.data.app.likes").isNumber())
			.andExpect(jsonPath("$.data.app.favorites").isNumber())
			.andExpect(jsonPath("$.data.app.comments").isNumber())
			.andExpect(jsonPath("$.data.watchTask.plays").isNumber())
			.andExpect(jsonPath("$.data.watchTask.points").isNumber())
			.andExpect(jsonPath("$.data.watchTask.averagePoints").isString())
			.andExpect(jsonPath("$.data.totalPoints").isString());
	}

	@Test
	void defaultRangeIsAllWithoutSince() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/statistics")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.range").value("all"))
			.andExpect(jsonPath("$.data.since").value(nullValue()));
	}

	@Test
	void invalidRangeReturnsBadRequest() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/statistics")
				.param("range", "quarter")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(400));
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
}
