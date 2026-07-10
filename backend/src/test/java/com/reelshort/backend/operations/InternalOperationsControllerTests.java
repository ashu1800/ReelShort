package com.reelshort.backend.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.reelshort.backend.admin.AdminAuditLogRepository;
import com.reelshort.backend.content.ContentBook;
import com.reelshort.backend.content.ContentBookCache;
import com.reelshort.backend.content.ContentBookCacheRepository;
import com.reelshort.backend.content.ContentEpisode;
import com.reelshort.backend.content.ContentEpisodeCache;
import com.reelshort.backend.content.ContentEpisodeCacheRepository;
import com.reelshort.backend.points.PointsService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.watch.WatchProgressRequest;
import com.reelshort.backend.watch.WatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "reelshort.internal.super-token=test-super-token")
@AutoConfigureMockMvc
class InternalOperationsControllerTests {

	private static final String TOKEN = "test-super-token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PointsService pointsService;

	@Autowired
	private WatchService watchService;

	@Autowired
	private ContentBookCacheRepository contentBookCacheRepository;

	@Autowired
	private ContentEpisodeCacheRepository contentEpisodeCacheRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private AdminAuditLogRepository adminAuditLogRepository;

	@Test
	void internalOperationsEndpointsRequireSuperToken() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(get("/api/internal/operations/users/{userId}/points/account", userId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("unauthorized"));

		mockMvc.perform(get("/api/internal/operations/users/{userId}/points/account", userId)
				.header("X-Internal-Super-Token", "wrong-token"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("forbidden"));
	}

	@Test
	void pointsAccountReturnsCurrentUserPointSnapshot() throws Exception {
		RegisteredUser user = createUser("+1", "4155550701", "Password123");
		pointsService.adjustByAdmin(user.userId(), 12, "seed points");

		mockMvc.perform(get("/api/internal/operations/users/{userId}/points/account", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(user.userId().toString()))
				.andExpect(jsonPath("$.data.account").value("+14155550701"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.balance").value(12))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(12));
	}

	@Test
	void watchRewardTaskUsesExistingProgressAndClaimedStages() throws Exception {
		RegisteredUser user = createUser("+1", "4155550702", "Password123");
		watchService.reportProgress(user.userId(), new WatchProgressRequest(
				"book-existing",
				"Existing Drama",
				"existing-drama",
				2,
				"chapter-2",
				24,
				100));

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-existing"))
				.andExpect(jsonPath("$.data.episodeNum").value(2))
				.andExpect(jsonPath("$.data.currentProgressPercent").value(24))
				.andExpect(jsonPath("$.data.nextRewardStage").value(25))
				.andExpect(jsonPath("$.data.targetProgressPercent").value(25))
				.andExpect(jsonPath("$.data.canReport").value(true));

		pointsService.awardWatchProgress(user.userId(), "book-existing", 2, 25);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nextRewardStage").value(50))
				.andExpect(jsonPath("$.data.alreadyClaimedStages[0]").value(25));
	}

	@Test
	void watchRewardTaskFallsBackToCachedContentWhenUserHasNoHistory() throws Exception {
		RegisteredUser user = createUser("+1", "4155550703", "Password123");
		clearContentCaches();
		seedCachedContent("book-cache", "Cached Drama", "cached-drama");

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-cache"))
				.andExpect(jsonPath("$.data.bookTitle").value("Cached Drama"))
				.andExpect(jsonPath("$.data.bookDescription").value("Cached description"))
				.andExpect(jsonPath("$.data.episodeNum").value(1))
				.andExpect(jsonPath("$.data.episodeTitle").value("Episode 1"))
				.andExpect(jsonPath("$.data.durationSeconds").value(300))
				.andExpect(jsonPath("$.data.nextRewardStage").value(25))
				.andExpect(jsonPath("$.data.canReport").value(true));
	}

	@Test
	void watchRewardTaskFallbackSkipsFullyClaimedCachedEpisode() throws Exception {
		RegisteredUser user = createUser("+1", "4155550708", "Password123");
		clearContentCaches();
		seedCachedContent("book-claimed-cache", "Claimed Drama", "claimed-drama");
		pointsService.awardWatchProgress(user.userId(), "book-claimed-cache", 1, 100);
		pointsService.awardWatchProgress(user.userId(), "book-claimed-cache", 2, 100);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("watch reward task not found"));
	}

	@Test
	void simulatedWatchProgressAwardsStagesIdempotently() throws Exception {
		RegisteredUser user = createUser("+1", "4155550704", "Password123");

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "book-sim",
						  "bookTitle": "Sim Drama",
						  "filteredTitle": "sim-drama",
						  "episodeNum": 1,
						  "chapterId": "chapter-1",
						  "positionSeconds": 300,
						  "durationSeconds": 300,
						  "progressPercent": 100,
						  "reason": "ops full simulation"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-sim"))
				.andExpect(jsonPath("$.data.progressPercent").value(100))
				.andExpect(jsonPath("$.data.awardedStages", containsInAnyOrder(25, 50, 75, 100)))
				.andExpect(jsonPath("$.data.awardedPoints").value(4))
				.andExpect(jsonPath("$.data.balance").value(4));

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "book-sim",
						  "bookTitle": "Sim Drama",
						  "filteredTitle": "sim-drama",
						  "episodeNum": 1,
						  "chapterId": "chapter-1",
						  "positionSeconds": 300,
						  "durationSeconds": 300,
						  "progressPercent": 100,
						  "reason": "ops duplicate simulation"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.awardedStages").isEmpty())
				.andExpect(jsonPath("$.data.awardedPoints").value(0))
				.andExpect(jsonPath("$.data.balance").value(4));
	}

	@Test
	void simulatedWatchProgressRejectsInvalidStageAndInactiveUsers() throws Exception {
		RegisteredUser user = createUser("+1", "4155550705", "Password123");

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressJson(30)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid progress stage"));

		changeStatus(user.userId(), UserStatus.BLACKLISTED);

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressJson(25)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("user is not active"));

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("user is not active"));
	}

	@Test
	void simulatedWatchProgressUsesDeclaredStageInsteadOfOversizedPosition() throws Exception {
		RegisteredUser user = createUser("+1", "4155550707", "Password123");

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "book-stage",
						  "bookTitle": "Stage Drama",
						  "filteredTitle": "stage-drama",
						  "episodeNum": 1,
						  "chapterId": "chapter-1",
						  "positionSeconds": 300,
						  "durationSeconds": 300,
						  "progressPercent": 25,
						  "reason": "ops stage simulation"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.progressPercent").value(25))
				.andExpect(jsonPath("$.data.awardedStages[0]").value(25))
				.andExpect(jsonPath("$.data.awardedPoints").value(1))
				.andExpect(jsonPath("$.data.balance").value(1));
	}

	@Test
	void simulatedWatchProgressDoesNotRegressExistingHigherProgress() throws Exception {
		RegisteredUser user = createUser("+1", "4155550709", "Password123");
		watchService.reportProgress(user.userId(), new WatchProgressRequest(
				"book-complete",
				"Complete Drama",
				"complete-drama",
				1,
				"chapter-1",
				300,
				300));

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "book-complete",
						  "bookTitle": "Complete Drama",
						  "filteredTitle": "complete-drama",
						  "episodeNum": 1,
						  "chapterId": "chapter-1",
						  "positionSeconds": 75,
						  "durationSeconds": 300,
						  "progressPercent": 25,
						  "reason": "ops no regression"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.progressPercent").value(100));
	}

	@Test
	void simulatedWatchProgressRejectsOverlongReasonBeforeAwardingPoints() throws Exception {
		RegisteredUser user = createUser("+1", "4155550710", "Password123");
		String longReason = "x".repeat(513);

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "book-long-reason",
						  "bookTitle": "Long Reason Drama",
						  "filteredTitle": "long-reason-drama",
						  "episodeNum": 1,
						  "chapterId": "chapter-1",
						  "positionSeconds": 75,
						  "durationSeconds": 300,
						  "progressPercent": 25,
						  "reason": "%s"
						}
						""".formatted(longReason)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/internal/operations/users/{userId}/points/account", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(0));
	}

	@Test
	void simulatedWatchProgressAuditsOperation() throws Exception {
		RegisteredUser user = createUser("+1", "4155550706", "Password123");

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "book-audit",
						  "bookTitle": "Audit Drama",
						  "filteredTitle": "audit-drama",
						  "episodeNum": 3,
						  "chapterId": "chapter-3",
						  "positionSeconds": 75,
						  "durationSeconds": 300,
						  "progressPercent": 25,
						  "reason": "ops audit simulation"
						}
						"""))
				.andExpect(status().isOk());

		String summary = adminAuditLogRepository.findAllByOrderByCreatedAtDesc().get(0).summary();
		assertThat(adminAuditLogRepository.findAllByOrderByCreatedAtDesc().get(0).adminUsername())
				.isEqualTo("internal-operations");
		assertThat(summary)
				.contains(user.userId().toString())
				.contains("book-audit")
				.contains("episode=3")
				.contains("progress=25")
				.contains("awardedPoints=1")
				.contains("ops audit simulation");
	}

	private RegisteredUser createUser(String countryCode, String phoneNumber, String password) throws Exception {
		String response = mockMvc.perform(post("/api/internal/users/register-phone")
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "countryCode": "%s",
						  "phoneNumber": "%s",
						  "password": "%s"
						}
						""".formatted(countryCode, phoneNumber, password)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return new RegisteredUser(
				UUID.fromString(JsonPath.read(response, "$.data.userId")),
				JsonPath.read(response, "$.data.phoneE164"));
	}

	private void seedCachedContent(String bookId, String title, String filteredTitle) throws Exception {
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(bookId, title, filteredTitle, "",
				"Cached description", 2)));
		String episodesJson = objectMapper.writeValueAsString(List.of(
				new ContentEpisode(1, "chapter-1", "Episode 1", "First episode"),
				new ContentEpisode(2, "chapter-2", "Episode 2", "Second episode")));
		contentEpisodeCacheRepository.save(ContentEpisodeCache.create(bookId, filteredTitle, episodesJson, 2));
	}

	private void clearContentCaches() {
		contentEpisodeCacheRepository.deleteAll();
		contentBookCacheRepository.deleteAll();
	}

	private void changeStatus(UUID userId, UserStatus status) {
		UserAccount user = userAccountRepository.findById(userId).orElseThrow();
		user.changeStatus(status);
		userAccountRepository.save(user);
	}

	private String progressJson(int progressPercent) {
		return """
				{
				  "bookId": "book-invalid",
				  "bookTitle": "Invalid Drama",
				  "filteredTitle": "invalid-drama",
				  "episodeNum": 1,
				  "chapterId": "chapter-1",
				  "positionSeconds": 75,
				  "durationSeconds": 300,
				  "progressPercent": %d,
				  "reason": "ops invalid simulation"
				}
				""".formatted(progressPercent);
	}

	private record RegisteredUser(UUID userId, String account) {
	}
}
