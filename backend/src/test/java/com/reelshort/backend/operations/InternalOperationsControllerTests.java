package com.reelshort.backend.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Random;
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
import com.reelshort.backend.content.ContentEpisodesDetail;
import com.reelshort.backend.content.ContentLocale;
import com.reelshort.backend.content.ContentProvider;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.web.servlet.MvcResult;

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

	@MockitoBean
	private ContentProvider contentProvider;

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
		// 用户没有任何观看记录，仅有的缓存短剧第 1 集已领奖 → 按「看过」跳过该剧 → 无候选返回 404。
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
	void watchRewardTaskFallsBackToLiveProviderWhenEpisodeCacheMissing() throws Exception {
		// 治本回归保护：book_cache 有书、episode_cache 缺失时，应通过 contentProvider 现拉剧集并返回任务，
		// 而非像旧实现那样因 episode_cache 为空直接跳过该书返回 404。
		RegisteredUser user = createUser("4155550712", "Password123");
		clearContentCaches();
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(
				"book-live", "Live Drama", "live-drama", "", "Live description", 2)));
		org.mockito.BDDMockito
				.given(contentProvider.getEpisodesDetail("book-live", "live-drama", ContentLocale.ENGLISH))
				.willReturn(new ContentEpisodesDetail(java.util.Optional.empty(), List.of(
						new ContentEpisode(1, "chapter-live-1", "Episode 1", "First episode"))));
		// runtime_cache 未 seed book-live，resolveDuration 会 fallback 到 getVideoUrl 现拉（同时回填 runtime_cache）。
		org.mockito.BDDMockito
				.given(contentProvider.getVideoUrl("book-live", 1, "live-drama", "chapter-live-1", ContentLocale.ENGLISH))
				.willReturn(new com.reelshort.backend.content.ContentVideo(
						"https://example.test/live.m3u8", 1, 300, null));

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-live"))
				.andExpect(jsonPath("$.data.bookTitle").value("Live Drama"))
				.andExpect(jsonPath("$.data.bookDescription").value("Live description"))
				.andExpect(jsonPath("$.data.episodeNum").value(1))
				.andExpect(jsonPath("$.data.chapterId").value("chapter-live-1"))
				.andExpect(jsonPath("$.data.episodeTitle").value("Episode 1"))
				.andExpect(jsonPath("$.data.durationSeconds").value(300))
				.andExpect(jsonPath("$.data.canReport").value(true));
	}

	@Test
	void watchRewardTaskDoesNotHoldDatabaseTransactionWhileCallingContentProvider() throws Exception {
		RegisteredUser user = createUser("4155550715", "Password123");
		clearContentCaches();
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(
				"book-no-tx", "No Tx Drama", "no-tx-drama", "", "No tx description", 1)));
		org.mockito.BDDMockito
				.given(contentProvider.getEpisodesDetail("book-no-tx", "no-tx-drama", ContentLocale.ENGLISH))
				.willAnswer(invocation -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return new ContentEpisodesDetail(java.util.Optional.empty(), List.of(
							new ContentEpisode(1, "chapter-no-tx-1", "Episode 1", "First episode")));
				});
		org.mockito.BDDMockito
				.given(contentProvider.getVideoUrl("book-no-tx", 1, "no-tx-drama", "chapter-no-tx-1",
						ContentLocale.ENGLISH))
				.willAnswer(invocation -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return new com.reelshort.backend.content.ContentVideo(
							"https://example.test/no-tx.m3u8", 1, 300, null);
				});

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-no-tx"))
				.andExpect(jsonPath("$.data.durationSeconds").value(300));
	}

	@Test
	void watchRewardTaskFollowsOpsFlowWithoutSwitchingDrama() throws Exception {
		// 运营脚本真实流程：GET 任务 → POST 上报 100% → 再 GET。同一部短剧必须按集数推进，
		// 且在没有新的观看记录时重复 GET 不能每次都换一部短剧的第一集。
		RegisteredUser user = createUser("4155550714", "Password123");
		clearContentCaches();
		seedCachedContent("book-old", "Old Drama", "old-drama", 2);
		seedCachedContent("book-new", "New Drama", "new-drama", 2);
		seedRuntimeDurations("book-old", 2);
		seedRuntimeDurations("book-new", 2);

		MvcResult firstTask = mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.episodeNum").value(1))
				.andReturn();
		String firstBookId = JsonPath.read(firstTask.getResponse().getContentAsString(), "$.data.bookId");
		String otherBookId = firstBookId.equals("book-old") ? "book-new" : "book-old";

		reportCompletedWatch(user.userId(), firstBookId, 1);

		// 有未领取记录（第 1 集已领奖 → 续播第 2 集）时，重复 GET 必须稳定返回同一 (短剧, 分集)：
		// 这条路径不经过随机取样，才真正覆盖「重复 GET 不换剧」。
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
					.header("X-Internal-Super-Token", TOKEN))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.bookId").value(firstBookId))
					.andExpect(jsonPath("$.data.episodeNum").value(2))
					.andExpect(jsonPath("$.data.currentProgressPercent").value(0));
		}

		reportCompletedWatch(user.userId(), firstBookId, 2);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value(otherBookId))
				.andExpect(jsonPath("$.data.episodeNum").value(1));
	}

	@Test
	void watchRewardTaskAdvancesToNextEpisodeWhenLatestEpisodeClaimed() throws Exception {
		RegisteredUser user = createUser("4155550717", "Password123");
		clearContentCaches();
		seedCachedContent("book-next", "Next Drama", "next-drama", 3);
		seedRuntimeDurations("book-next", 3);
		// 用户从第 2 集开始看（没有第 1 集记录），看完第 2 集后应续到第 3 集。
		reportCompletedWatch(user.userId(), "book-next", 2);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-next"))
				.andExpect(jsonPath("$.data.episodeNum").value(3))
				.andExpect(jsonPath("$.data.chapterId").value("chapter-3"))
				.andExpect(jsonPath("$.data.episodeTitle").value("Episode 3"))
				.andExpect(jsonPath("$.data.currentProgressPercent").value(0))
				.andExpect(jsonPath("$.data.estimatedRewardPoints").value(5))
				.andExpect(jsonPath("$.data.canReport").value(true));
	}

	@Test
	void watchRewardTaskSkipsAlreadyClaimedEpisodesInsideDrama() throws Exception {
		// 重看旧集会让「最新记录」回到较早的集：续播必须跳过其后所有已领奖集，不能 off-by-one。
		RegisteredUser user = createUser("4155550721", "Password123");
		clearContentCaches();
		seedCachedContent("book-skip", "Skip Drama", "skip-drama", 4);
		seedRuntimeDurations("book-skip", 4);
		reportCompletedWatch(user.userId(), "book-skip", 1);
		reportCompletedWatch(user.userId(), "book-skip", 2);
		reportCompletedWatch(user.userId(), "book-skip", 3);
		reportCompletedWatch(user.userId(), "book-skip", 2);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-skip"))
				.andExpect(jsonPath("$.data.episodeNum").value(4))
				.andExpect(jsonPath("$.data.currentProgressPercent").value(0));
	}

	@Test
	void watchRewardTaskFallsBackWhenLatestEpisodeIsBeyondEpisodeList() throws Exception {
		// 记录集号大于剧集列表（缓存过期或上游分集变动）时必须安全回退，不能抛异常。
		RegisteredUser user = createUser("4155550722", "Password123");
		clearContentCaches();
		seedCachedContent("book-beyond", "Beyond Drama", "beyond-drama", 2);
		seedCachedContent("book-target", "Target Drama", "target-drama", 2);
		seedRuntimeDurations("book-beyond", 5);
		seedRuntimeDurations("book-target", 2);
		reportCompletedWatch(user.userId(), "book-beyond", 5);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-target"))
				.andExpect(jsonPath("$.data.episodeNum").value(1));
	}

	@Test
	void watchRewardTaskKeepsEpisodeWatchedBeforeVipWithoutClaim() throws Exception {
		// 非 VIP 期间看完不写领取记录：升级 VIP 后必须仍返回同一集（可补发），不能跳到下一集。
		com.reelshort.backend.TestAppUsers.RegisteredUser plain = com.reelshort.backend.TestAppUsers
				.register(mockMvc, objectMapper, "4155550723", "Password123");
		clearContentCaches();
		seedCachedContent("book-claimless", "Claimless Drama", "claimless-drama", 2);
		seedRuntimeDurations("book-claimless", 2);
		watchService.reportProgress(plain.userId(), new WatchProgressRequest("book-claimless", "Claimless Drama",
				"claimless-drama", 1, "chapter-1", 300, 300));
		grantVip(plain.userId());

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", plain.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-claimless"))
				.andExpect(jsonPath("$.data.episodeNum").value(1))
				.andExpect(jsonPath("$.data.currentProgressPercent").value(100));
	}

	@Test
	void watchRewardTaskReturnsNotFoundWhenEveryCachedDramaIsWatched() throws Exception {
		// 用户看完全部候选片库：没有「从未观看的短剧」可用时保持既有 404 契约。
		RegisteredUser user = createUser("4155550724", "Password123");
		clearContentCaches();
		seedCachedContent("book-only", "Only Drama", "only-drama", 2);
		seedRuntimeDurations("book-only", 2);
		reportCompletedWatch(user.userId(), "book-only", 1);
		reportCompletedWatch(user.userId(), "book-only", 2);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("watch reward task not found"));
	}

	@Test
	void watchRewardTaskDoesNotHoldTransactionOnContinuationPath() throws Exception {
		// 续播分支（取剧集列表 + 逐集取权威时长）同样必须在无数据库事务的上下文里调用内容源。
		RegisteredUser user = createUser("4155550725", "Password123");
		clearContentCaches();
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(
				"book-tx-cont", "Tx Cont Drama", "tx-cont-drama", "", "Tx cont description", 2)));
		runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create("book-tx-cont", 1, "chapter-1", 300));
		org.mockito.BDDMockito
				.given(contentProvider.getEpisodesDetail("book-tx-cont", "tx-cont-drama", ContentLocale.ENGLISH))
				.willAnswer(invocation -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return new ContentEpisodesDetail(java.util.Optional.empty(), List.of(
							new ContentEpisode(1, "chapter-1", "Episode 1", "First episode"),
							new ContentEpisode(2, "chapter-2", "Episode 2", "Second episode")));
				});
		org.mockito.BDDMockito
				.given(contentProvider.getVideoUrl("book-tx-cont", 2, "tx-cont-drama", "chapter-2",
						ContentLocale.ENGLISH))
				.willAnswer(invocation -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return new com.reelshort.backend.content.ContentVideo(
							"https://example.test/tx-cont-2.m3u8", 2, 300, null);
				});
		reportCompletedWatch(user.userId(), "book-tx-cont", 1);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-tx-cont"))
				.andExpect(jsonPath("$.data.episodeNum").value(2))
				.andExpect(jsonPath("$.data.durationSeconds").value(300));
	}

	@Test
	void watchRewardTaskFallsBackToUnwatchedDramaAfterLatestDramaFinished() throws Exception {
		RegisteredUser user = createUser("4155550718", "Password123");
		clearContentCaches();
		seedCachedContent("book-finished", "Finished Drama", "finished-drama", 2);
		seedCachedContent("book-fresh", "Fresh Drama", "fresh-drama", 2);
		seedRuntimeDurations("book-finished", 2);
		seedRuntimeDurations("book-fresh", 2);
		reportCompletedWatch(user.userId(), "book-finished", 1);
		reportCompletedWatch(user.userId(), "book-finished", 2);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-fresh"))
				.andExpect(jsonPath("$.data.episodeNum").value(1))
				.andExpect(jsonPath("$.data.chapterId").value("chapter-1"))
				.andExpect(jsonPath("$.data.canReport").value(true));
	}

	@Test
	void watchRewardTaskIgnoresOlderUnfinishedDramaWhenLatestDramaFinished() throws Exception {
		// 续播只认最近一条观看记录所属短剧：更早的短剧还有没看完的分集也不回退续播。
		RegisteredUser user = createUser("4155550720", "Password123");
		clearContentCaches();
		seedCachedContent("book-y", "Y Drama", "y-drama", 2);
		seedCachedContent("book-x", "X Drama", "x-drama", 2);
		seedCachedContent("book-z", "Z Drama", "z-drama", 2);
		seedRuntimeDurations("book-y", 2);
		seedRuntimeDurations("book-x", 2);
		seedRuntimeDurations("book-z", 2);
		reportPartialWatch(user.userId(), "book-y", 1, 90);
		reportCompletedWatch(user.userId(), "book-x", 1);
		reportCompletedWatch(user.userId(), "book-x", 2);

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-z"))
				.andExpect(jsonPath("$.data.episodeNum").value(1));
	}

	@Test
	void watchRewardTaskRejectsNonVipUser() throws Exception {
		com.reelshort.backend.TestAppUsers.RegisteredUser plain = com.reelshort.backend.TestAppUsers
				.register(mockMvc, objectMapper, "4155550719", "Password123");

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", plain.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("non-VIP users cannot earn watch rewards"));
	}

	@Test
	void watchRewardTaskLogsWarnWhenProviderCallFailsForCandidateBook() throws Exception {
		// P3-19：跳过某本书时必须留下 WARN 日志（记录 bookId 与异常摘要），不再静默吞错。
		RegisteredUser user = createUser("4155550716", "Password123");
		clearContentCaches();
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(
				"book-warn", "Warn Drama", "warn-drama", "", "Warn description", 1)));
		org.mockito.BDDMockito
				.given(contentProvider.getEpisodesDetail("book-warn", "warn-drama", ContentLocale.ENGLISH))
				.willThrow(new com.reelshort.backend.content.ContentProviderException(503, "upstream unavailable"));

		ch.qos.logback.classic.Logger operationsLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger(com.reelshort.backend.operations.InternalOperationsService.class);
		ch.qos.logback.classic.Level previousLevel = operationsLogger.getLevel();
		ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
				new ch.qos.logback.core.read.ListAppender<>();
		appender.start();
		operationsLogger.addAppender(appender);
		operationsLogger.setLevel(ch.qos.logback.classic.Level.WARN);
		try {
			mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
					.header("X-Internal-Super-Token", TOKEN))
					.andExpect(status().isNotFound());
		}
		finally {
			operationsLogger.detachAppender(appender);
			operationsLogger.setLevel(previousLevel);
		}

		assertThat(appender.list)
				.anySatisfy(event -> {
					assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
					assertThat(event.getFormattedMessage()).contains("book-warn").contains("ContentProviderException");
				});
	}

	@Test
	void watchRewardTaskLogsWarnWhenDurationLookupFailsForLatestEpisode() throws Exception {
		// R1 回归：最近那集取不到权威时长（内容源异常）时必须留下 WARN，并退化为随机换剧；
		// 旧实现在这里静默吞掉异常，用户当前短剧被换掉却没有任何日志可查。
		RegisteredUser user = createUser("4155550726", "Password123");
		clearContentCaches();
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(
				"book-dur-fail", "Dur Fail Drama", "dur-fail-drama", "", "Dur fail description", 2)));
		seedCachedContent("book-dur-ok", "Dur Ok Drama", "dur-ok-drama", 2);
		seedRuntimeDurations("book-dur-ok", 2);
		// 未看完第 1 集（40%）：不产生领取记录；该集权威时长缓存缺失且视频接口 503。
		watchService.reportProgress(user.userId(), new WatchProgressRequest("book-dur-fail", "Dur Fail Drama",
				"dur-fail-drama", 1, "chapter-1", 120, 300));
		org.mockito.BDDMockito
				.given(contentProvider.getVideoUrl("book-dur-fail", 1, "dur-fail-drama", "chapter-1",
						ContentLocale.ENGLISH))
				.willThrow(new com.reelshort.backend.content.ContentProviderException(503,
						"video upstream unavailable"));

		ch.qos.logback.classic.Logger operationsLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger(com.reelshort.backend.operations.InternalOperationsService.class);
		ch.qos.logback.classic.Level previousLevel = operationsLogger.getLevel();
		ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
				new ch.qos.logback.core.read.ListAppender<>();
		appender.start();
		operationsLogger.addAppender(appender);
		operationsLogger.setLevel(ch.qos.logback.classic.Level.WARN);
		try {
			// 该集无法上报（POST 要求权威时长），因此退化为另一部未观看短剧的第 1 集。
			mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
					.header("X-Internal-Super-Token", TOKEN))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.bookId").value("book-dur-ok"))
					.andExpect(jsonPath("$.data.episodeNum").value(1));
		}
		finally {
			operationsLogger.detachAppender(appender);
			operationsLogger.setLevel(previousLevel);
		}

		assertThat(appender.list)
				.anySatisfy(event -> {
					assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
					assertThat(event.getFormattedMessage()).contains("book-dur-fail")
							.contains("ContentProviderException");
				});
	}

	@Test
	void watchRewardTaskSkipsBookWhenProviderFailsAndContinuesToNext() throws Exception {
		// 鲁棒性回归保护：当上游 provider 对某本书返回 5xx（episode_cache 缺失需现拉），
		// 应跳过该书继续遍历下一本，而非让整次调用失败或返回 404。
		// 不依赖遍历顺序：无论 book-fail 还是 book-ok 先被遇到，最终都必须返回 book-ok。
		RegisteredUser user = createUser("4155550713", "Password123");
		clearContentCaches();
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(
				"book-fail", "Fail Drama", "fail-drama", "", "Fail description", 2)));
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(
				"book-ok", "Ok Drama", "ok-drama", "", "Ok description", 2)));
		// book-fail：provider 返回 503，模拟上游不可用。
		org.mockito.BDDMockito
				.given(contentProvider.getEpisodesDetail("book-fail", "fail-drama", ContentLocale.ENGLISH))
				.willThrow(new com.reelshort.backend.content.ContentProviderException(503, "upstream unavailable"));
		// book-ok：provider 正常返回，runtime_cache 缺失时 resolveDuration 走 getVideoUrl 兜底。
		org.mockito.BDDMockito
				.given(contentProvider.getEpisodesDetail("book-ok", "ok-drama", ContentLocale.ENGLISH))
				.willReturn(new ContentEpisodesDetail(java.util.Optional.empty(), List.of(
						new ContentEpisode(1, "chapter-ok-1", "Episode 1", "First episode"))));
		org.mockito.BDDMockito
				.given(contentProvider.getVideoUrl("book-ok", 1, "ok-drama", "chapter-ok-1", ContentLocale.ENGLISH))
				.willReturn(new com.reelshort.backend.content.ContentVideo(
						"https://example.test/ok.m3u8", 1, 300, null));

		mockMvc.perform(get("/api/internal/operations/users/{userId}/watch-reward-task", user.userId())
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookId").value("book-ok"))
				.andExpect(jsonPath("$.data.bookTitle").value("Ok Drama"))
				.andExpect(jsonPath("$.data.episodeNum").value(1))
				.andExpect(jsonPath("$.data.durationSeconds").value(300))
				.andExpect(jsonPath("$.data.canReport").value(true));
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
		seedCachedContent(bookId, title, filteredTitle, 2);
	}

	private void seedCachedContent(String bookId, String title, String filteredTitle, int episodeCount)
			throws Exception {
		contentBookCacheRepository.save(ContentBookCache.from(new ContentBook(bookId, title, filteredTitle, "",
				"Cached description", episodeCount)));
		List<ContentEpisode> episodes = new java.util.ArrayList<>();
		for (int episode = 1; episode <= episodeCount; episode++) {
			episodes.add(new ContentEpisode(episode, "chapter-" + episode, "Episode " + episode,
					"Episode " + episode + " description"));
		}
		contentEpisodeCacheRepository.save(ContentEpisodeCache.create(bookId, filteredTitle,
				objectMapper.writeValueAsString(episodes), episodeCount));
	}

	/** 权威时长缓存：章节编号与 seedCachedContent 的 chapter-N 约定保持一致。 */
	private void seedRuntimeDurations(String bookId, int episodeCount) {
		for (int episode = 1; episode <= episodeCount; episode++) {
			runtimeCacheRepository.save(ContentEpisodeRuntimeCache.create(bookId, episode, "chapter-" + episode, 300));
		}
	}

	/** 走运营上报接口模拟「看完一集」：同时写入观看记录与领取记录，与真实运营脚本流程一致。 */
	private void reportCompletedWatch(UUID userId, String bookId, int episodeNum) throws Exception {
		DramaInfo info = drama(bookId);
		mockMvc.perform(post("/api/internal/operations/users/{userId}/watch-progress", userId)
				.header("X-Internal-Super-Token", TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "%s",
						  "bookTitle": "%s",
						  "filteredTitle": "%s",
						  "episodeNum": %d,
						  "chapterId": "chapter-%d",
						  "positionSeconds": 300,
						  "durationSeconds": 300,
						  "progressPercent": 100,
						  "reason": "ops flow simulation"
						}
						""".formatted(bookId, info.title(), info.filteredTitle(), episodeNum, episodeNum)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.progressPercent").value(100));
	}

	/** 直接走观看服务上报未完成进度：只写观看记录，不产生领取记录。 */
	private void reportPartialWatch(UUID userId, String bookId, int episodeNum, int positionSeconds) {
		DramaInfo info = drama(bookId);
		watchService.reportProgress(userId, new WatchProgressRequest(bookId, info.title(), info.filteredTitle(),
				episodeNum, "chapter-" + episodeNum, positionSeconds, 300));
	}

	private static DramaInfo drama(String bookId) {
		DramaInfo info = DRAMAS.get(bookId);
		assertThat(info).as("test drama metadata for %s", bookId).isNotNull();
		return info;
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

	private static final Map<String, DramaInfo> DRAMAS = Map.ofEntries(
			Map.entry("book-old", new DramaInfo("Old Drama", "old-drama")),
			Map.entry("book-new", new DramaInfo("New Drama", "new-drama")),
			Map.entry("book-next", new DramaInfo("Next Drama", "next-drama")),
			Map.entry("book-finished", new DramaInfo("Finished Drama", "finished-drama")),
			Map.entry("book-fresh", new DramaInfo("Fresh Drama", "fresh-drama")),
			Map.entry("book-y", new DramaInfo("Y Drama", "y-drama")),
			Map.entry("book-x", new DramaInfo("X Drama", "x-drama")),
			Map.entry("book-z", new DramaInfo("Z Drama", "z-drama")),
			Map.entry("book-skip", new DramaInfo("Skip Drama", "skip-drama")),
			Map.entry("book-beyond", new DramaInfo("Beyond Drama", "beyond-drama")),
			Map.entry("book-target", new DramaInfo("Target Drama", "target-drama")),
			Map.entry("book-only", new DramaInfo("Only Drama", "only-drama")),
			Map.entry("book-tx-cont", new DramaInfo("Tx Cont Drama", "tx-cont-drama")));

	private record DramaInfo(String title, String filteredTitle) {
	}

	private record RegisteredUser(UUID userId, String account) {
	}

	@TestConfiguration
	static class RandomOrderConfiguration {

		@Bean
		@Primary
		Random watchRewardTaskRandom() {
			return new Random() {
				@Override
				public int nextInt(int bound) {
					return 0;
				}
			};
		}
	}
}
