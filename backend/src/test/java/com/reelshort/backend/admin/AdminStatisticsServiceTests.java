package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.reelshort.backend.admin.AdminStatisticsResponse.AppMetrics;
import com.reelshort.backend.admin.AdminStatisticsResponse.WatchTaskMetrics;
import com.reelshort.backend.points.PointAccount;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.WatchEpisodeRewardClaimRepository;
import com.reelshort.backend.social.Comment;
import com.reelshort.backend.social.CommentRepository;
import com.reelshort.backend.social.Favorite;
import com.reelshort.backend.social.FavoriteRepository;
import com.reelshort.backend.social.Like;
import com.reelshort.backend.social.LikeRepository;
import com.reelshort.backend.watch.WatchRecord;
import com.reelshort.backend.watch.WatchRecordRepository;
import com.reelshort.backend.watch.WatchSource;

@SpringBootTest
class AdminStatisticsServiceTests {

	private static final ZoneId POINTS_ZONE = ZoneId.of("Asia/Shanghai");
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	@Autowired
	private AdminStatisticsService adminStatisticsService;

	@Autowired
	private WatchRecordRepository watchRecordRepository;

	@Autowired
	private LikeRepository likeRepository;

	@Autowired
	private FavoriteRepository favoriteRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository;

	@Autowired
	private PointAccountRepository pointAccountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearStatisticsTables() {
		// 统计口径是全局计数；先清空，避免同一测试上下文里其它用例的数据干扰精确断言。
		watchEpisodeRewardClaimRepository.deleteAll();
		watchRecordRepository.deleteAll();
		likeRepository.deleteAll();
		favoriteRepository.deleteAll();
		commentRepository.deleteAll();
		pointAccountRepository.deleteAll();
	}

	@Test
	void statisticsAggregatesEachWindowAndOperationsTaskPoints() {
		OffsetDateTime now = OffsetDateTime.now();
		UUID appUser = UUID.randomUUID();
		UUID taskUser = UUID.randomUUID();

		// App 自然观看：今天 2 条、3 天前 1 条、12 天前 1 条、40 天前 1 条（按行数计，重看不重复）。
		watch(appUser, "stats-app-1", WatchSource.APP, now);
		watch(appUser, "stats-app-2", WatchSource.APP, now);
		watch(appUser, "stats-app-3d", WatchSource.APP, now.minusDays(3));
		watch(appUser, "stats-app-12d", WatchSource.APP, now.minusDays(12));
		watch(appUser, "stats-app-40d", WatchSource.APP, now.minusDays(40));

		// 运营任务观看与领取：今天 2 条（10 + 5 分）、3 天前 6 分、12 天前 6 分、40 天前 7 分。
		watch(taskUser, "stats-task-1", WatchSource.OPERATIONS, now);
		rewardClaim(taskUser, "stats-task-1", 10);
		watch(taskUser, "stats-task-2", WatchSource.OPERATIONS, now);
		rewardClaim(taskUser, "stats-task-2", 5);
		watch(taskUser, "stats-task-3d", WatchSource.OPERATIONS, now.minusDays(3));
		rewardClaim(taskUser, "stats-task-3d", 6);
		watch(taskUser, "stats-task-12d", WatchSource.OPERATIONS, now.minusDays(12));
		rewardClaim(taskUser, "stats-task-12d", 6);
		watch(taskUser, "stats-task-40d", WatchSource.OPERATIONS, now.minusDays(40));
		rewardClaim(taskUser, "stats-task-40d", 7);
		// 干扰项：App 自然观看同键也有领取记录，来源不是 OPERATIONS，不得计入任务积分。
		rewardClaim(appUser, "stats-app-1", 999);

		like(appUser, "stats-like-now", now);
		like(appUser, "stats-like-10d", now.minusDays(10));
		like(appUser, "stats-like-40d", now.minusDays(40));
		favorite(appUser, "stats-favorite-now", now);
		favorite(appUser, "stats-favorite-12d", now.minusDays(12));
		comment(appUser, "stats-comment-now", now);
		comment(appUser, "stats-comment-40d", now.minusDays(40));

		// 两个含十分位的账户：100.5 + 23.1 = 123.6。
		pointAccount(appUser, 1005);
		pointAccount(taskUser, 231);

		LocalDate today = LocalDate.now(POINTS_ZONE);

		AdminStatisticsResponse todayStats = adminStatisticsService.statistics(AdminStatisticsRange.TODAY);
		assertThat(todayStats.range()).isEqualTo("today");
		assertThat(todayStats.since())
			.isEqualTo(TIMESTAMP.format(today.atStartOfDay(POINTS_ZONE).toOffsetDateTime()));
		assertThat(todayStats.app()).isEqualTo(new AppMetrics(2, 1, 1, 1));
		assertThat(todayStats.watchTask()).isEqualTo(new WatchTaskMetrics(2, 15, "7.50"));
		assertThat(todayStats.totalPoints()).isEqualTo("123.6");

		AdminStatisticsResponse sevenDays = adminStatisticsService.statistics(AdminStatisticsRange.SEVEN_DAYS);
		assertThat(sevenDays.range()).isEqualTo("7d");
		assertThat(sevenDays.since())
			.isEqualTo(TIMESTAMP.format(today.minusDays(6).atStartOfDay(POINTS_ZONE).toOffsetDateTime()));
		assertThat(sevenDays.app()).isEqualTo(new AppMetrics(3, 1, 1, 1));
		assertThat(sevenDays.watchTask()).isEqualTo(new WatchTaskMetrics(3, 21, "7.00"));
		assertThat(sevenDays.totalPoints()).isEqualTo("123.6");

		AdminStatisticsResponse thirtyDays = adminStatisticsService.statistics(AdminStatisticsRange.THIRTY_DAYS);
		assertThat(thirtyDays.range()).isEqualTo("30d");
		assertThat(thirtyDays.since())
			.isEqualTo(TIMESTAMP.format(today.minusDays(29).atStartOfDay(POINTS_ZONE).toOffsetDateTime()));
		assertThat(thirtyDays.app()).isEqualTo(new AppMetrics(4, 2, 2, 1));
		assertThat(thirtyDays.watchTask()).isEqualTo(new WatchTaskMetrics(4, 27, "6.75"));
		assertThat(thirtyDays.totalPoints()).isEqualTo("123.6");

		AdminStatisticsResponse all = adminStatisticsService.statistics(AdminStatisticsRange.ALL);
		assertThat(all.range()).isEqualTo("all");
		assertThat(all.since()).isNull();
		assertThat(all.app()).isEqualTo(new AppMetrics(5, 3, 2, 2));
		assertThat(all.watchTask()).isEqualTo(new WatchTaskMetrics(5, 34, "6.80"));
		assertThat(all.totalPoints()).isEqualTo("123.6");
	}

	@Test
	void agedRowsLeaveTodayWindowButRemainInAllWindow() {
		OffsetDateTime now = OffsetDateTime.now();
		UUID userId = UUID.randomUUID();
		watch(userId, "stats-aging-app", WatchSource.APP, now);
		watch(userId, "stats-aging-task", WatchSource.OPERATIONS, now);
		rewardClaim(userId, "stats-aging-task", 9);
		like(userId, "stats-aging-like", now);
		favorite(userId, "stats-aging-favorite", now);
		comment(userId, "stats-aging-comment", now);
		// 7 分：验证总积分是整数时不带 ".0"。
		pointAccount(userId, 70);

		AdminStatisticsResponse fresh = adminStatisticsService.statistics(AdminStatisticsRange.TODAY);
		assertThat(fresh.app()).isEqualTo(new AppMetrics(1, 1, 1, 1));
		assertThat(fresh.watchTask()).isEqualTo(new WatchTaskMetrics(1, 9, "9.00"));
		assertThat(fresh.totalPoints()).isEqualTo("7");

		// 把观看与互动的行为时间改到 40 天前：today 窗口应归零，all 仍按全量计数。
		OffsetDateTime aged = now.minusDays(40);
		jdbcTemplate.update("update watch_records set updated_at = ? where user_id = ?", aged, userId);
		jdbcTemplate.update("update likes set created_at = ? where user_id = ?", aged, userId);
		jdbcTemplate.update("update favorites set created_at = ? where user_id = ?", aged, userId);
		jdbcTemplate.update("update comments set created_at = ? where user_id = ?", aged, userId);

		AdminStatisticsResponse agedToday = adminStatisticsService.statistics(AdminStatisticsRange.TODAY);
		assertThat(agedToday.app()).isEqualTo(new AppMetrics(0, 0, 0, 0));
		assertThat(agedToday.watchTask()).isEqualTo(new WatchTaskMetrics(0, 0, "0.00"));
		assertThat(agedToday.totalPoints()).isEqualTo("7");

		AdminStatisticsResponse agedAll = adminStatisticsService.statistics(AdminStatisticsRange.ALL);
		assertThat(agedAll.since()).isNull();
		assertThat(agedAll.app()).isEqualTo(new AppMetrics(1, 1, 1, 1));
		assertThat(agedAll.watchTask()).isEqualTo(new WatchTaskMetrics(1, 9, "9.00"));
		assertThat(agedAll.totalPoints()).isEqualTo("7");
	}

	private void watch(UUID userId, String bookId, WatchSource source, OffsetDateTime updatedAt) {
		WatchRecord record = WatchRecord.create(userId, bookId, "Title " + bookId, "title-" + bookId, 1,
				"chapter-" + bookId, source);
		record.update("Title " + bookId, "title-" + bookId, "chapter-" + bookId, 100, 100, 100);
		WatchRecord saved = watchRecordRepository.saveAndFlush(record);
		jdbcTemplate.update("update watch_records set updated_at = ? where id = ?", updatedAt, saved.id());
	}

	private void rewardClaim(UUID userId, String bookId, int awardedPoints) {
		jdbcTemplate.update("""
				insert into watch_episode_reward_claims (
					id, user_id, book_id, episode_num, duration_seconds,
					calculated_points, calculated_tenths, awarded_points, created_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), userId, bookId, 1, 100, awardedPoints, awardedPoints * 10, awardedPoints,
				OffsetDateTime.now());
	}

	private void like(UUID userId, String bookId, OffsetDateTime createdAt) {
		Like like = likeRepository.saveAndFlush(Like.create(userId, bookId));
		jdbcTemplate.update("update likes set created_at = ? where id = ?", createdAt, like.id());
	}

	private void favorite(UUID userId, String bookId, OffsetDateTime createdAt) {
		Favorite favorite = favoriteRepository
			.saveAndFlush(Favorite.create(userId, bookId, "Title " + bookId, "title-" + bookId, null, 1));
		jdbcTemplate.update("update favorites set created_at = ? where id = ?", createdAt, favorite.id());
	}

	private void comment(UUID userId, String bookId, OffsetDateTime createdAt) {
		Comment comment = commentRepository
			.saveAndFlush(Comment.create(userId, "stats-user", bookId, "comment " + bookId));
		jdbcTemplate.update("update comments set created_at = ? where id = ?", createdAt, comment.id());
	}

	private void pointAccount(UUID userId, int totalTenths) {
		PointAccount account = PointAccount.create(userId);
		account.addTenths(totalTenths);
		pointAccountRepository.saveAndFlush(account);
	}
}
