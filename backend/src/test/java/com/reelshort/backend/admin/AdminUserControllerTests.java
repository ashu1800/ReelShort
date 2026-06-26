package com.reelshort.backend.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class AdminUserControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void adminCanListUsersWithPointBalance() throws Exception {
		String adminToken = adminLogin();
		registerAppUser("admin-list-alice");

		mockMvc.perform(get("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].username", hasItem("admin-list-alice")))
				.andExpect(jsonPath("$.data[*].status", hasItem("ACTIVE")));
	}

	@Test
	void adminCanViewUserDetail() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-detail-alice");
		reportProgress(user.token(), "detail-book", 1, 80, 100);

		mockMvc.perform(get("/api/admin/users/{userId}", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(user.userId().toString()))
				.andExpect(jsonPath("$.data.username").value("admin-detail-alice"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.pointBalance").value(3))
				.andExpect(jsonPath("$.data.watchRecordCount").value(1))
				.andExpect(jsonPath("$.data.pointRecordCount").value(3));
	}

	@Test
	void adminCanDisableUserAndAppAccessFails() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-disable-alice");

		mockMvc.perform(post("/api/admin/users/{userId}/status", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "status": "DISABLED"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DISABLED"));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403));
	}

	@Test
	void adminCanQueryOneUsersWatchRecordsAndPointRecords() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser first = registerAppUser("admin-activity-alice");
		RegisteredUser second = registerAppUser("admin-activity-bob");
		reportProgress(first.token(), "first-book", 1, 80, 100);
		reportProgress(second.token(), "second-book", 1, 100, 100);

		mockMvc.perform(get("/api/admin/users/{userId}/watch-records", first.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("first-book"));

		mockMvc.perform(get("/api/admin/users/{userId}/point-records", first.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(3)))
				.andExpect(jsonPath("$.data[0].source").value("WATCH_REWARD"));
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

	private RegisteredUser registerAppUser(String username) throws Exception {
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
		return new RegisteredUser(UUID.fromString(response.path("data").path("userId").asText()),
				response.path("data").path("token").asText());
	}

	private void reportProgress(String token, String bookId, int episodeNum, int positionSeconds, int durationSeconds)
			throws Exception {
		mockMvc.perform(post("/api/app/watch/progress")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "%s",
						  "bookTitle": "Title %s",
						  "filteredTitle": "filtered-%s",
						  "episodeNum": %d,
						  "chapterId": "chapter-%d",
						  "positionSeconds": %d,
						  "durationSeconds": %d
						}
						""".formatted(bookId, bookId, bookId, episodeNum, episodeNum, positionSeconds, durationSeconds)))
				.andExpect(status().isOk());
	}

	private record RegisteredUser(UUID userId, String token) {
	}
}
