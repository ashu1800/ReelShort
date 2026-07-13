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
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.content.ContentEpisodeRuntimeCache;
import com.reelshort.backend.content.ContentEpisodeRuntimeCacheRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PointsControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ContentEpisodeRuntimeCacheRepository runtimeCacheRepository;

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
				.andExpect(jsonPath("$.data.awardedPoints").value(0))
				.andExpect(jsonPath("$.data.rewardStatus").value("NOT_COMPLETE"));

		reportProgress(token, "book-1", 1, 120, 120)
				.andExpect(jsonPath("$.data.awardedPoints").value(2))
				.andExpect(jsonPath("$.data.awardedStages", hasSize(0)))
				.andExpect(jsonPath("$.data.rewardStatus").value("AWARDED"));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(2));

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].amount").value(2))
				.andExpect(jsonPath("$.data[0].source").value("WATCH_REWARD"))
				.andExpect(jsonPath("$.data[0].stage").doesNotExist());
	}

	@Test
	void watchProgressJumpAwardsAllReachedStages() throws Exception {
		String token = registerAndExtractToken("points-dan");

		reportProgress(token, "book-2", 2, 100, 100)
				.andExpect(jsonPath("$.data.awardedPoints").value(1))
				.andExpect(jsonPath("$.data.awardedStages", hasSize(0)))
				.andExpect(jsonPath("$.data.rewardStatus").value("AWARDED"));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(1));
	}

	private ResultActionsFacade reportProgress(String token, String bookId, int episodeNum, int positionSeconds,
			int durationSeconds) throws Exception {
		ContentEpisodeRuntimeCache runtime = runtimeCacheRepository
				.findByBookIdAndEpisodeNumAndChapterId(bookId, episodeNum, "chapter-" + episodeNum)
				.orElseGet(() -> ContentEpisodeRuntimeCache.create(bookId, episodeNum, "chapter-" + episodeNum,
						durationSeconds));
		runtime.update(durationSeconds);
		runtimeCacheRepository.save(runtime);
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
		return TestAppUsers.token(mockMvc, objectMapper, username);
	}

	private record ResultActionsFacade(org.springframework.test.web.servlet.ResultActions resultActions) {
		ResultActionsFacade andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
			resultActions.andExpect(matcher);
			return this;
		}
	}
}
