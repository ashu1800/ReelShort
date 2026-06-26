package com.reelshort.backend.points;

import static org.hamcrest.Matchers.hasSize;
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
class PointsControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void accountStartsWithZeroBalance() throws Exception {
		String token = registerAndExtractToken("points-alice");

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.balance").value(0));
	}

	@Test
	void recordsStartEmpty() throws Exception {
		String token = registerAndExtractToken("points-bob");

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));
	}

	@Test
	void watchProgressAwardsStageOnceAndCreatesTransaction() throws Exception {
		String token = registerAndExtractToken("points-carol");

		reportProgress(token, "book-1", 1, 30, 120)
				.andExpect(jsonPath("$.data.awardedPoints").value(1))
				.andExpect(jsonPath("$.data.awardedStages", hasSize(1)))
				.andExpect(jsonPath("$.data.awardedStages[0]").value(25));

		reportProgress(token, "book-1", 1, 35, 120)
				.andExpect(jsonPath("$.data.awardedPoints").value(0))
				.andExpect(jsonPath("$.data.awardedStages", hasSize(0)));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(1));

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].amount").value(1))
				.andExpect(jsonPath("$.data[0].source").value("WATCH_REWARD"))
				.andExpect(jsonPath("$.data[0].stage").value(25));
	}

	@Test
	void watchProgressJumpAwardsAllReachedStages() throws Exception {
		String token = registerAndExtractToken("points-dan");

		reportProgress(token, "book-2", 2, 80, 100)
				.andExpect(jsonPath("$.data.awardedPoints").value(3))
				.andExpect(jsonPath("$.data.awardedStages", hasSize(3)))
				.andExpect(jsonPath("$.data.awardedStages[0]").value(25))
				.andExpect(jsonPath("$.data.awardedStages[1]").value(50))
				.andExpect(jsonPath("$.data.awardedStages[2]").value(75));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(3));
	}

	private ResultActionsFacade reportProgress(String token, String bookId, int episodeNum, int positionSeconds,
			int durationSeconds) throws Exception {
		return new ResultActionsFacade(mockMvc.perform(post("/api/app/watch/progress")
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
				.andExpect(status().isOk()));
	}

	private String registerAndExtractToken(String username) throws Exception {
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

	private record ResultActionsFacade(org.springframework.test.web.servlet.ResultActions resultActions) {
		ResultActionsFacade andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
			resultActions.andExpect(matcher);
			return this;
		}
	}
}
