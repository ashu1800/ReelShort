package com.reelshort.backend.operations;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.content.ContentBookCache;
import com.reelshort.backend.content.ContentBookCacheRepository;
import com.reelshort.backend.content.ContentCacheService;
import com.reelshort.backend.content.ContentEpisode;
import com.reelshort.backend.content.ContentEpisodeRuntimeCacheRepository;
import com.reelshort.backend.content.ContentLocale;
import com.reelshort.backend.points.DailyEarningQuotaResponse;
import com.reelshort.backend.points.PointAccountResponse;
import com.reelshort.backend.points.PointsService;
import com.reelshort.backend.points.WatchEpisodeRewardClaimRepository;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.watch.WatchProgressRequest;
import com.reelshort.backend.watch.WatchRecord;
import com.reelshort.backend.watch.WatchRecordRepository;
import com.reelshort.backend.watch.WatchRecordResponse;
import com.reelshort.backend.watch.WatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalOperationsService {

	private final UserAccountRepository userAccountRepository;
	private final PointsService pointsService;
	private final WatchService watchService;
	private final WatchRecordRepository watchRecordRepository;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ContentEpisodeRuntimeCacheRepository runtimeCacheRepository;
	private final WatchEpisodeRewardClaimRepository rewardClaimRepository;
	private final ContentCacheService contentCacheService;
	private final AdminAuditService adminAuditService;

	public InternalOperationsService(UserAccountRepository userAccountRepository, PointsService pointsService,
			WatchService watchService, WatchRecordRepository watchRecordRepository,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeRuntimeCacheRepository runtimeCacheRepository,
			WatchEpisodeRewardClaimRepository rewardClaimRepository,
			ContentCacheService contentCacheService, AdminAuditService adminAuditService) {
		this.userAccountRepository = userAccountRepository;
		this.pointsService = pointsService;
		this.watchService = watchService;
		this.watchRecordRepository = watchRecordRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.runtimeCacheRepository = runtimeCacheRepository;
		this.rewardClaimRepository = rewardClaimRepository;
		this.contentCacheService = contentCacheService;
		this.adminAuditService = adminAuditService;
	}

	public InternalPointsAccountResponse pointsAccount(UUID userId) {
		UserAccount user = user(userId);
		return InternalPointsAccountResponse.from(user, pointsService.account(user.id()));
	}

	@Transactional
	public InternalWatchRewardTaskResponse watchRewardTask(UUID userId) {
		UserAccount user = activeUser(userId);
		if (!user.isVip()) {
			throw new AuthException(403, "non-VIP users cannot earn watch rewards");
		}
		for (WatchRecord record : watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(user.id())) {
			InternalWatchRewardTaskResponse task = taskFromRecord(user.id(), record);
			if (task != null && task.canReport()) {
				return task;
			}
		}
		InternalWatchRewardTaskResponse task = taskFromContentCache(user.id());
		if (task == null) {
			throw new AuthException(404, "watch reward task not found");
		}
		return task;
	}

	@Transactional
	public InternalWatchProgressResponse reportWatchProgress(UUID userId, InternalWatchProgressRequest request) {
		UserAccount user = activeUser(userId);
		if (request.progressPercent() != 100) {
			throw new AuthException(400, "completed progress required");
		}
		int duration = runtimeCacheRepository
				.findByBookIdAndEpisodeNumAndChapterId(request.bookId(), request.episodeNum(), request.chapterId())
				.map(cache -> cache.durationSeconds())
				.orElseThrow(() -> new AuthException(409, "video duration unavailable"));
		WatchRecordResponse watchRecord = watchService.reportProgress(user.id(), new WatchProgressRequest(
				request.bookId(), request.bookTitle(), request.filteredTitle(), request.episodeNum(), request.chapterId(),
				duration, duration));
		PointAccountResponse account = pointsService.account(user.id());
		adminAuditService.record("internal-operations", "SIMULATE_WATCH_REWARD", "USER", user.id(),
				"Simulated completed watch reward userId=%s bookId=%s episode=%d duration=%d awardedPoints=%d status=%s reason=%s"
						.formatted(user.id(), watchRecord.bookId(), watchRecord.episodeNum(), duration,
								watchRecord.awardedPoints(), watchRecord.rewardStatus(), request.reason().trim()));
		return InternalWatchProgressResponse.from(watchRecord, account);
	}

	private InternalWatchRewardTaskResponse taskFromRecord(UUID userId, WatchRecord record) {
		if (rewardClaimRepository.existsByUserIdAndBookIdAndEpisodeNum(userId, record.bookId(), record.episodeNum())) {
			return null;
		}
		int duration = resolveDuration(record.bookId(), record.episodeNum(), record.filteredTitle(), record.chapterId());
		if (duration <= 0) {
			return null;
		}
		return task(userId, record.bookId(), record.bookTitle(), "", record.filteredTitle(), record.episodeNum(),
				record.chapterId(), "Episode " + record.episodeNum(), duration, record.progressPercent());
	}

	private InternalWatchRewardTaskResponse taskFromContentCache(UUID userId) {
		// 一次性预取该用户全部已领奖的 (bookId, episodeNum) 集合，避免逐集 N+1 查询。
		Set<String> claimed = rewardClaimRepository.findClaimedKeysByUserId(userId);
		for (ContentBookCache book : contentBookCacheRepository
				.findByLocaleAndChapterCountGreaterThanOrderByUpdatedAtDesc(ContentLocale.ENGLISH, 0)) {
			// 通过 getEpisodes 获取剧集列表：episode_cache 命中则秒回，缺失时向上游 provider 现拉并回填，
			// 使候选池覆盖全部 ENGLISH 剧，而非仅限已预热的少数剧集。
			List<ContentEpisode> episodes;
			try {
				episodes = contentCacheService.getEpisodes(book.bookId(), book.filteredTitle(), ContentLocale.ENGLISH);
			}
			catch (RuntimeException exception) {
				// 上游 5xx / 404 或解析失败时跳过这本书，不阻断整体遍历。
				continue;
			}
			if (episodes == null || episodes.isEmpty()) {
				continue;
			}
			for (ContentEpisode episode : episodes) {
				if (claimed.contains(book.bookId() + "#" + episode.episode())) {
					continue;
				}
				int duration = resolveDuration(book.bookId(), episode.episode(), book.filteredTitle(), episode.chapterId());
				if (duration > 0) {
					return task(userId, book.bookId(), book.title(), book.description(), book.filteredTitle(),
							episode.episode(), episode.chapterId(), episode.title(), duration, 0);
				}
			}
		}
		return null;
	}

	private InternalWatchRewardTaskResponse task(UUID userId, String bookId, String bookTitle, String bookDescription,
			String filteredTitle, int episodeNum, String chapterId, String episodeTitle, int durationSeconds,
			int currentProgressPercent) {
		DailyEarningQuotaResponse quota = pointsService.dailyEarningQuota(userId);
		return new InternalWatchRewardTaskResponse(bookId, bookTitle, bookDescription, filteredTitle, episodeNum,
				chapterId, episodeTitle, durationSeconds, currentProgressPercent, null, null, List.of(), true,
				pointsService.estimatedWatchRewardPoints(durationSeconds), quota.effectiveMaximum(), quota.earnedPoints(),
				quota.remainingPoints(), false);
	}

	private int resolveDuration(String bookId, int episodeNum, String filteredTitle, String chapterId) {
		return runtimeCacheRepository.findByBookIdAndEpisodeNumAndChapterId(bookId, episodeNum, chapterId)
				.map(cache -> cache.durationSeconds())
				.orElseGet(() -> {
					try {
						return contentCacheService.getVideoUrl(bookId, episodeNum, filteredTitle, chapterId,
								ContentLocale.ENGLISH).duration();
					}
					catch (RuntimeException exception) {
						return 0;
					}
				});
	}

	private UserAccount activeUser(UUID userId) {
		UserAccount user = user(userId);
		if (user.status() != UserStatus.ACTIVE) {
			throw new AuthException(403, "user is not active");
		}
		return user;
	}

	private UserAccount user(UUID userId) {
		return userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthException(404, "user not found"));
	}
}
