package com.reelshort.backend.points;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {

	List<PointTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

	List<PointTransaction> findByUserIdAndBookIdAndEpisodeNumAndSourceOrderByStageAsc(UUID userId, String bookId,
			Integer episodeNum, String source);

	@Query("""
			select coalesce(sum(transaction.amount), 0)
			from PointTransaction transaction
			where transaction.userId = :userId
			  and transaction.source = 'WATCH_REWARD'
			  and transaction.createdAt >= :startInclusive
			  and transaction.createdAt < :endExclusive
			""")
	long sumWatchRewardAmountByUserIdAndCreatedAtBetween(
			@Param("userId") UUID userId,
			@Param("startInclusive") OffsetDateTime startInclusive,
			@Param("endExclusive") OffsetDateTime endExclusive);

	long countByUserId(UUID userId);
}
