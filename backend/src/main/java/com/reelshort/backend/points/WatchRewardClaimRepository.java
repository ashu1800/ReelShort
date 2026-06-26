package com.reelshort.backend.points;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchRewardClaimRepository extends JpaRepository<WatchRewardClaim, UUID> {

	boolean existsByUserIdAndBookIdAndEpisodeNumAndStage(UUID userId, String bookId, int episodeNum, int stage);
}
