package com.reelshort.backend.points;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WatchEpisodeRewardClaimRepository extends JpaRepository<WatchEpisodeRewardClaim, UUID> {

	boolean existsByUserIdAndBookIdAndEpisodeNum(UUID userId, String bookId, int episodeNum);

	@Query("select claim.awardedPoints from WatchEpisodeRewardClaim claim "
			+ "where claim.userId = :userId and claim.bookId = :bookId and claim.episodeNum = :episodeNum")
	Optional<Integer> findAwardedPoints(UUID userId, String bookId, int episodeNum);
}
