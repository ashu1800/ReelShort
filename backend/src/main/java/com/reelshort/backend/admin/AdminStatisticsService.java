package com.reelshort.backend.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminStatisticsResponse.AppMetrics;
import com.reelshort.backend.admin.AdminStatisticsResponse.WatchTaskMetrics;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.WatchEpisodeRewardClaimRepository;
import com.reelshort.backend.social.CommentRepository;
import com.reelshort.backend.social.FavoriteRepository;
import com.reelshort.backend.social.LikeRepository;
import com.reelshort.backend.watch.WatchRecordRepository;
import com.reelshort.backend.watch.WatchSource;

/**
 * 后台数据统计：App 自然观看量、社交互动量、运营观看任务（刷积分）量与全站积分快照。
 *
 * <p>口径说明：</p>
 * <ul>
 * <li>观看量按 {@code watch_records} 行数统计，每用户每集一行，重看不重复计；更新时间为观看行为时间。</li>
 * <li>运营任务积分按 (user_id, book_id, episode_num) 与观看记录同键 join 领取表，来源过滤 OPERATIONS，
 * 避免把 App 自然观看的奖励算进任务积分。</li>
 * <li>时间窗按 Asia/Shanghai 自然日切分：today 为今天 00:00 起，7d 含今天共 7 天，30d 共 30 天，all 不限。</li>
 * <li>totalPoints 是当前全量积分账户快照，与时间窗无关。</li>
 * </ul>
 */
@Service
public class AdminStatisticsService {

	private static final ZoneId POINTS_ZONE = ZoneId.of("Asia/Shanghai");
	private static final DateTimeFormatter RESPONSE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	private final WatchRecordRepository watchRecordRepository;
	private final LikeRepository likeRepository;
	private final FavoriteRepository favoriteRepository;
	private final CommentRepository commentRepository;
	private final WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository;
	private final PointAccountRepository pointAccountRepository;
	private final Clock clock;

	public AdminStatisticsService(WatchRecordRepository watchRecordRepository, LikeRepository likeRepository,
			FavoriteRepository favoriteRepository, CommentRepository commentRepository,
			WatchEpisodeRewardClaimRepository watchEpisodeRewardClaimRepository,
			PointAccountRepository pointAccountRepository, Clock clock) {
		this.watchRecordRepository = watchRecordRepository;
		this.likeRepository = likeRepository;
		this.favoriteRepository = favoriteRepository;
		this.commentRepository = commentRepository;
		this.watchEpisodeRewardClaimRepository = watchEpisodeRewardClaimRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AdminStatisticsResponse statistics(AdminStatisticsRange range) {
		LocalDate today = LocalDate.now(clock.withZone(POINTS_ZONE));
		OffsetDateTime since = switch (range) {
			case TODAY -> startOfDay(today);
			case SEVEN_DAYS -> startOfDay(today.minusDays(6));
			case THIRTY_DAYS -> startOfDay(today.minusDays(29));
			case ALL -> null;
		};
		long appPlays = since == null ? watchRecordRepository.countBySource(WatchSource.APP)
				: watchRecordRepository.countBySourceAndUpdatedAtGreaterThanEqual(WatchSource.APP, since);
		long taskPlays = since == null ? watchRecordRepository.countBySource(WatchSource.OPERATIONS)
				: watchRecordRepository.countBySourceAndUpdatedAtGreaterThanEqual(WatchSource.OPERATIONS, since);
		long likes = since == null ? likeRepository.count() : likeRepository.countByCreatedAtGreaterThanEqual(since);
		long favorites = since == null ? favoriteRepository.count()
				: favoriteRepository.countByCreatedAtGreaterThanEqual(since);
		long comments = since == null ? commentRepository.count()
				: commentRepository.countByCreatedAtGreaterThanEqual(since);
		long taskPoints = since == null ? watchEpisodeRewardClaimRepository.sumOperationsTaskAwardedPoints()
				: watchEpisodeRewardClaimRepository.sumOperationsTaskAwardedPointsSince(since);
		return new AdminStatisticsResponse(range.value(),
				since == null ? null : RESPONSE_TIMESTAMP.format(since),
				new AppMetrics(appPlays, likes, favorites, comments),
				new WatchTaskMetrics(taskPlays, taskPoints, averagePoints(taskPoints, taskPlays)),
				totalPoints());
	}

	/** 单次任务平均积分：保留 2 位小数、HALF_UP；没有任务播放时固定 "0.00"。 */
	private static String averagePoints(long points, long plays) {
		if (plays == 0) {
			return "0.00";
		}
		return BigDecimal.valueOf(points)
				.divide(BigDecimal.valueOf(plays), 2, RoundingMode.HALF_UP)
				.toPlainString();
	}

	/** 当前全站积分快照：(整数余额 * 10 + 十分位尾数) / 10，保留 1 位小数并去掉无意义尾零。 */
	private String totalPoints() {
		long totalTenths = pointAccountRepository.sumBalance() * 10 + pointAccountRepository.sumFractionalPart();
		if (totalTenths == 0) {
			return "0";
		}
		return BigDecimal.valueOf(totalTenths).movePointLeft(1).stripTrailingZeros().toPlainString();
	}

	private static OffsetDateTime startOfDay(LocalDate date) {
		return date.atStartOfDay(POINTS_ZONE).toOffsetDateTime();
	}
}
