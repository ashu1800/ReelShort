package com.reelshort.backend.points;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchEpisodeRewardClaimRepository extends JpaRepository<WatchEpisodeRewardClaim, UUID> {

	boolean existsByUserIdAndBookIdAndEpisodeNum(UUID userId, String bookId, int episodeNum);

	@Query("select claim.awardedPoints from WatchEpisodeRewardClaim claim "
			+ "where claim.userId = :userId and claim.bookId = :bookId and claim.episodeNum = :episodeNum")
	Optional<Integer> findAwardedPoints(UUID userId, String bookId, int episodeNum);

	@Query("select concat(claim.bookId, '#', claim.episodeNum) from WatchEpisodeRewardClaim claim "
			+ "where claim.userId = :userId")
	Set<String> findClaimedKeysByUserId(@Param("userId") UUID userId);

	@Query("select claim.calculatedTenths from WatchEpisodeRewardClaim claim "
			+ "where claim.userId = :userId and claim.bookId = :bookId and claim.episodeNum = :episodeNum")
	Optional<Integer> findCalculatedTenths(UUID userId, String bookId, int episodeNum);

	/**
	 * 运营观看任务积分：只统计同键（用户 + 短剧 + 集数）观看记录来源为 OPERATIONS 的领取记录。
	 * 同键保证重看不重复计，来源过滤保证 App 自然观看的奖励不计入任务积分。
	 */
	@Query(value = """
			select coalesce(sum(c.awarded_points), 0)
			from watch_episode_reward_claims c
			join watch_records w on w.user_id = c.user_id and w.book_id = c.book_id and w.episode_num = c.episode_num
			where w.source = 'OPERATIONS'
			""", nativeQuery = true)
	Long sumOperationsTaskAwardedPoints();

	@Query(value = """
			select coalesce(sum(c.awarded_points), 0)
			from watch_episode_reward_claims c
			join watch_records w on w.user_id = c.user_id and w.book_id = c.book_id and w.episode_num = c.episode_num
			where w.source = 'OPERATIONS' and w.updated_at >= :since
			""", nativeQuery = true)
	Long sumOperationsTaskAwardedPointsSince(@Param("since") OffsetDateTime since);
}
