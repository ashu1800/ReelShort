package com.reelshort.backend.points;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {

	List<PointTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

	List<PointTransaction> findByUserIdAndBookIdAndEpisodeNumAndSourceOrderByStageAsc(UUID userId, String bookId,
			Integer episodeNum, String source);

	long countByUserId(UUID userId);
}
