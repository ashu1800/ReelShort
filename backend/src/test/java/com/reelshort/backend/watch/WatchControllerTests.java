package com.reelshort.backend.watch;

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
class WatchControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void progressRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/app/watch/progress")
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressBody("book-1", "Love Story", "love-story", 1, "chapter-1", 30, 120)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void progressReportCreatesWatchRecord() throws Exception {
		String token = registerAndExtractToken("watch-alice");

		mockMvc.perform(post("/api/app/watch/progress")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressBody("book-1", "Love Story", "love-story", 1, "chapter-1", 30, 120)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.bookId").value("book-1"))
				.andExpect(jsonPath("$.data.episodeNum").value(1))
				.andExpect(jsonPath("$.data.positionSeconds").value(30))
				.andExpect(jsonPath("$.data.durationSeconds").value(120))
				.andExpect(jsonPath("$.data.progressPercent").value(25));
	}

	@Test
	void duplicateProgressReportUpdatesExistingRecord() throws Exception {
		String token = registerAndExtractToken("watch-bob");
		reportProgress(token, "book-1", 1, 30, 120);

		mockMvc.perform(post("/api/app/watch/progress")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressBody("book-1", "Love Story", "love-story", 1, "chapter-1", 90, 120)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.positionSeconds").value(90))
				.andExpect(jsonPath("$.data.progressPercent").value(75));

		mockMvc.perform(get("/api/app/watch/history")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].positionSeconds").value(90));
	}

	@Test
	void historyIsScopedToCurrentUserAndSortedNewestFirst() throws Exception {
		String token = registerAndExtractToken("watch-carol");
		String otherToken = registerAndExtractToken("watch-dan");
		reportProgress(token, "book-1", 1, 30, 120);
		reportProgress(otherToken, "book-1", 1, 80, 120);
		reportProgress(token, "book-2", 3, 60, 100);

		mockMvc.perform(get("/api/app/watch/history")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].bookId").value("book-2"))
				.andExpect(jsonPath("$.data[1].bookId").value("book-1"));
	}

	@Test
	void progressRejectsInvalidDuration() throws Exception {
		String token = registerAndExtractToken("watch-erin");

		mockMvc.perform(post("/api/app/watch/progress")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressBody("book-1", "Love Story", "love-story", 1, "chapter-1", 30, 0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	private void reportProgress(String token, String bookId, int episodeNum, int positionSeconds, int durationSeconds)
			throws Exception {
		mockMvc.perform(post("/api/app/watch/progress")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressBody(bookId, "Title " + bookId, "filtered-" + bookId, episodeNum,
						"chapter-" + episodeNum, positionSeconds, durationSeconds)))
				.andExpect(status().isOk());
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

	private String progressBody(String bookId, String bookTitle, String filteredTitle, int episodeNum, String chapterId,
			int positionSeconds, int durationSeconds) {
		return """
				{
				  "bookId": "%s",
				  "bookTitle": "%s",
				  "filteredTitle": "%s",
				  "episodeNum": %d,
				  "chapterId": "%s",
				  "positionSeconds": %d,
				  "durationSeconds": %d
				}
				""".formatted(bookId, bookTitle, filteredTitle, episodeNum, chapterId, positionSeconds, durationSeconds);
	}
}
