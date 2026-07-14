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
import com.reelshort.backend.content.ContentEpisodeRuntimeCache;
import com.reelshort.backend.content.ContentEpisodeRuntimeCacheRepository;
import com.reelshort.backend.points.DailyEarningRuleRepository;
import com.reelshort.backend.points.PointsService;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.watch.WatchProgressRequest;
import com.reelshort.backend.watch.WatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
	private ContentEpisodeRuntimeCacheRepository runtimeCacheRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private AdminAuditLogRepository adminAuditLogRepository;

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	private DailyEarningRuleRepository dailyEarningRuleRepository;

	@AfterEach
	void resetConfigs() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "60");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "35");
		dailyEarningRuleRepository.deleteAll();
	}

	@BeforeEach
	void seedAuthoritativeDurations() {
		runtimeCacheRepository.deleteAll();
		for (String bookId : List.of("book-sim", "book-sim-cap", "book-stage", "book-complete",
				"book-long-reason", "book-invalid")) {
			runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create(bookId, 1, "chapter-1", 300));
		}
		runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create("book-existing", 2, "chapter-2", 100));
		runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create("book-audit", 3, "chapter-3", 300));
		runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create("book-cache", 1, "chapter-1", 300));
		runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create("book-cache", 2, "chapter-2", 300));
		runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create("book-claimed-cache", 1, "chapter-1", 300));
		runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create("book-claimed-cache", 2, "chapter-2", 300));
	}

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
		RegisteredUser user = createUser("4155550701", "Password123");
		pointsService.adjustByAdmin(user.userId(), 12, "seed points");

		mockMvc.perform(get("/api/internal/operations/users/{userId}/points/account", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(user.userId().toString()))
				.andExpect(jsonPath("$.data.account").value(user.account()))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.balance").value(12))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(12));
	}

	@Test
	void watchRewardTaskUsesExistingProgressAndDurationReward() throws Exception {
		RegisteredUser user = createUser("4155550702", "Password123");
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
				.andExpect(jsonPath("$.data.nextRewardStage").doesNotExist())
				.andExpect(jsonPath("$.data.targetProgressPercent").doesNotExist())
				.andExpect(jsonPath("$.data.estimatedRewardPoints").value(1))
				.andExpect(jsonPath("$.data.canReport").value(true));

		pointsService.awardWatchProgress(user.userId(), "book-existing", 2, 100, 100);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.rewardClaimed").value(false));
	}

	@Test
	void watchRewardTaskFallsBackToCachedContentWhenUserHasNoHistory() throws Exception {
		RegisteredUser user = createUser("4155550703", "Password123");
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
				.andExpect(jsonPath("$.data.estimatedRewardPoints").value(5))
				.andExpect(jsonPath("$.data.canReport").value(true));
	}

	@Test
	void watchRewardTaskFallbackSkipsFullyClaimedCachedEpisode() throws Exception {
		RegisteredUser user = createUser("4155550708", "Password123");
		clearContentCaches();
		seedCachedContent("book-claimed-cache", "Claimed Drama", "claimed-drama");
		pointsService.awardWatchProgress(user.userId(), "book-claimed-cache", 1, 100, 300);
		pointsService.awardWatchProgress(user.userId(), "book-claimed-cache", 2, 100, 300);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("watch reward task not found"));
	}

	@Test
	void simulatedWatchProgressAwardsDurationRewardIdempotently() throws Exception {
		RegisteredUser user = createUser("4155550704", "Password123");

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
				.andExpect(jsonPath("$.data.awardedStages").isEmpty())
				.andExpect(jsonPath("$.data.awardedPoints").value(5))
				.andExpect(jsonPath("$.data.rewardStatus").value("AWARDED"))
				.andExpect(jsonPath("$.data.balance").value(5));

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
				.andExpect(jsonPath("$.data.rewardStatus").value("ALREADY_CLAIMED"))
				.andExpect(jsonPath("$.data.balance").value(5));
	}

	@Test
	void simulatedWatchProgressUsesDailyEarnedMaximum() throws Exception {
		dailyEarningRuleRepository.deleteAll();
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "0");
		RegisteredUser user = createUser("4155550711", "Password123");

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "book-sim-cap",
						  "bookTitle": "Sim Cap Drama",
						  "filteredTitle": "sim-cap-drama",
						  "episodeNum": 1,
						  "chapterId": "chapter-1",
						  "positionSeconds": 300,
						  "durationSeconds": 300,
						  "progressPercent": 100,
						  "reason": "ops capped simulation"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.awardedStages").isEmpty())
				.andExpect(jsonPath("$.data.awardedPoints").value(1))
				.andExpect(jsonPath("$.data.rewardStatus").value("AWARDED_PARTIAL"))
				.andExpect(jsonPath("$.data.balance").value(1));
	}

	@Test
	void simulatedWatchProgressRejectsInvalidStageAndInactiveUsers() throws Exception {
		RegisteredUser user = createUser("4155550705", "Password123");

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressJson(30)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("completed progress required"));

		changeStatus(user.userId(), UserStatus.BLACKLISTED);

		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", user.userId())
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(progressJson(100)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("user is not active"));

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("user is not active"));
	}

	@Test
	void simulatedWatchProgressRejectsIncompleteProgress() throws Exception {
		RegisteredUser user = createUser("4155550707", "Password123");

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
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("completed progress required"));
	}

	@Test
	void simulatedWatchProgressDoesNotRegressExistingHigherProgress() throws Exception {
		RegisteredUser user = createUser("4155550709", "Password123");
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
						  "progressPercent": 100,
						  "reason": "ops no regression"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.progressPercent").value(100));
	}

	@Test
	void simulatedWatchProgressRejectsOverlongReasonBeforeAwardingPoints() throws Exception {
		RegisteredUser user = createUser("4155550710", "Password123");
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
						  "positionSeconds": 300,
						  "durationSeconds": 300,
						  "progressPercent": 100,
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
		RegisteredUser user = createUser("4155550706", "Password123");

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
						  "positionSeconds": 300,
						  "durationSeconds": 300,
						  "progressPercent": 100,
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
				.contains("duration=300")
				.contains("awardedPoints=5")
				.contains("ops audit simulation");
	}

	private RegisteredUser createUser(String seed, String password) throws Exception {
		com.reelshort.backend.TestAppUsers.RegisteredUser user =
				com.reelshort.backend.TestAppUsers.register(mockMvc, objectMapper, seed, password);
		grantVip(user.userId());
		return new RegisteredUser(user.userId(), user.username());
	}

	private void grantVip(UUID userId) {
		UserAccount user = userAccountRepository.findById(userId).orElseThrow();
		user.grantVip(java.time.OffsetDateTime.now().plusDays(1));
		userAccountRepository.save(user);
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
