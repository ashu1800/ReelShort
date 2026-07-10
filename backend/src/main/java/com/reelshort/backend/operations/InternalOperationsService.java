package com.reelshort.backend.operations;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.content.ContentBookCache;
import com.reelshort.backend.content.ContentBookCacheRepository;
import com.reelshort.backend.content.ContentEpisode;
import com.reelshort.backend.content.ContentEpisodeCache;
import com.reelshort.backend.content.ContentEpisodeCacheRepository;
import com.reelshort.backend.content.ContentLocale;
import com.reelshort.backend.points.PointAccountResponse;
import com.reelshort.backend.points.PointTransaction;
import com.reelshort.backend.points.PointTransactionRepository;
import com.reelshort.backend.points.PointsService;
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

	private static final List<Integer> REWARD_STAGES = List.of(25, 50, 75, 100);
	private static final Set<Integer> VALID_PROGRESS_STAGES = Set.of(25, 50, 75, 100);
	private static final int DEFAULT_DURATION_SECONDS = 300;

	private final UserAccountRepository userAccountRepository;
	private final PointsService pointsService;
	private final WatchService watchService;
	private final WatchRecordRepository watchRecordRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final ContentBookCacheRepository contentBookCacheRepository;
	private final ContentEpisodeCacheRepository contentEpisodeCacheRepository;
	private final AdminAuditService adminAuditService;
	private final ObjectMapper objectMapper;

	public InternalOperationsService(UserAccountRepository userAccountRepository, PointsService pointsService,
			WatchService watchService, WatchRecordRepository watchRecordRepository,
			PointTransactionRepository pointTransactionRepository,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			AdminAuditService adminAuditService, ObjectMapper objectMapper) {
		this.userAccountRepository = userAccountRepository;
		this.pointsService = pointsService;
		this.watchService = watchService;
		this.watchRecordRepository = watchRecordRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.contentBookCacheRepository = contentBookCacheRepository;
		this.contentEpisodeCacheRepository = contentEpisodeCacheRepository;
		this.adminAuditService = adminAuditService;
		this.objectMapper = objectMapper;
	}

	public InternalPointsAccountResponse pointsAccount(UUID userId) {
		UserAccount user = user(userId);
		return InternalPointsAccountResponse.from(user, pointsService.account(user.id()));
	}

	@Transactional(readOnly = true)
	public InternalWatchRewardTaskResponse watchRewardTask(UUID userId) {
		UserAccount user = activeUser(userId);
		return watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(user.id()).stream()
				.map(record -> taskFromRecord(record, claimedStages(user.id(), record.bookId(), record.episodeNum())))
				.filter(InternalWatchRewardTaskResponse::canReport)
				.findFirst()
				.orElseGet(() -> taskFromContentCache(user.id()));
	}

	@Transactional
	public InternalWatchProgressResponse reportWatchProgress(UUID userId, InternalWatchProgressRequest request) {
		UserAccount user = activeUser(userId);
		if (!VALID_PROGRESS_STAGES.contains(request.progressPercent())) {
			throw new AuthException(400, "invalid progress stage");
		}
		int targetPosition = targetPosition(user.id(), request);
		WatchRecordResponse watchRecord = watchService.reportProgress(user.id(), new WatchProgressRequest(
				request.bookId(),
				request.bookTitle(),
				request.filteredTitle(),
				request.episodeNum(),
				request.chapterId(),
				targetPosition,
				request.durationSeconds()));
		PointAccountResponse account = pointsService.account(user.id());
		adminAuditService.record("internal-operations", "SIMULATE_WATCH_REWARD", "USER", user.id(),
				"Simulated watch reward userId=%s bookId=%s episode=%d progress=%d awardedStages=%s awardedPoints=%d reason=%s"
						.formatted(user.id(), watchRecord.bookId(), watchRecord.episodeNum(),
								watchRecord.progressPercent(), watchRecord.awardedStages(), watchRecord.awardedPoints(),
								request.reason().trim()));
		return InternalWatchProgressResponse.from(watchRecord, account);
	}

	private InternalWatchRewardTaskResponse taskFromRecord(WatchRecord record, List<Integer> claimedStages) {
		Integer nextStage = nextStage(claimedStages);
		boolean canReport = nextStage != null;
		return new InternalWatchRewardTaskResponse(
				record.bookId(),
				record.bookTitle(),
				"",
				record.filteredTitle(),
				record.episodeNum(),
				record.chapterId(),
				"Episode " + record.episodeNum(),
				record.durationSeconds() > 0 ? record.durationSeconds() : DEFAULT_DURATION_SECONDS,
				record.progressPercent(),
				nextStage,
				nextStage,
				claimedStages,
				canReport);
	}

	private InternalWatchRewardTaskResponse taskFromContentCache(UUID userId) {
		return contentBookCacheRepository
				.findByLocaleAndChapterCountGreaterThanOrderByUpdatedAtDesc(ContentLocale.ENGLISH, 0)
				.stream()
				.map(book -> taskFromCachedBook(userId, book))
				.filter(task -> task != null)
				.findFirst()
				.orElseThrow(() -> new AuthException(404, "watch reward task not found"));
	}

	private InternalWatchRewardTaskResponse taskFromCachedBook(UUID userId, ContentBookCache book) {
		return contentEpisodeCacheRepository
				.findFirstByBookIdAndLocaleAndEpisodeCountGreaterThanOrderByRefreshedAtDesc(
						book.bookId(), ContentLocale.ENGLISH, 0)
				.map(episodeCache -> taskFromCachedEpisodes(userId, book, episodeCache))
				.orElse(null);
	}

	private InternalWatchRewardTaskResponse taskFromCachedEpisodes(UUID userId, ContentBookCache book,
			ContentEpisodeCache episodeCache) {
		for (ContentEpisode episode : episodes(episodeCache)) {
			List<Integer> claimedStages = claimedStages(userId, book.bookId(), episode.episode());
			Integer nextStage = nextStage(claimedStages);
			if (nextStage != null) {
				return new InternalWatchRewardTaskResponse(
						book.bookId(),
						book.title(),
						book.description(),
						book.filteredTitle(),
						episode.episode(),
						episode.chapterId(),
						episode.title(),
						DEFAULT_DURATION_SECONDS,
						0,
						nextStage,
						nextStage,
						claimedStages,
						true);
			}
		}
		return null;
	}

	private List<Integer> claimedStages(UUID userId, String bookId, int episodeNum) {
		return pointTransactionRepository
				.findByUserIdAndBookIdAndEpisodeNumAndSourceOrderByStageAsc(userId, bookId, episodeNum,
						"WATCH_REWARD")
				.stream()
				.map(PointTransaction::stage)
				.filter(stage -> stage != null)
				.distinct()
				.toList();
	}

	private Integer nextStage(List<Integer> claimedStages) {
		return REWARD_STAGES.stream()
				.filter(stage -> !claimedStages.contains(stage))
				.findFirst()
				.orElse(null);
	}

	private List<ContentEpisode> episodes(ContentEpisodeCache episodeCache) {
		try {
			List<ContentEpisode> episodes = objectMapper.readValue(episodeCache.episodesJson(),
					new TypeReference<List<ContentEpisode>>() {
					});
			return episodes;
		}
		catch (Exception exception) {
			return List.of();
		}
	}

	private int targetPosition(UUID userId, InternalWatchProgressRequest request) {
		int requestedStagePosition = (int) Math.ceil(request.durationSeconds() * request.progressPercent() / 100.0);
		return watchRecordRepository.findByUserIdAndBookIdAndEpisodeNum(userId, request.bookId(), request.episodeNum())
				.map(record -> Math.max(record.positionSeconds(), requestedStagePosition))
				.orElse(requestedStagePosition);
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
