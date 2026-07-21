package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

	List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select request from WithdrawalRequest request where request.id = :withdrawalId")
	Optional<WithdrawalRequest> findByIdForUpdate(UUID withdrawalId);

	long countByUserId(UUID userId);

	List<WithdrawalRequest> findAllByOrderByCreatedAtDesc();

	@Query("select coalesce(sum(request.usdtAmount), 0) as totalUsdt, count(request) as payoutCount "
			+ "from WithdrawalRequest request where request.network = 'ERC20' "
			+ "and request.status = com.reelshort.backend.withdrawal.WithdrawalStatus.APPROVED "
			+ "and request.reviewedAt >= :from and request.reviewedAt < :to")
	WithdrawalStatsAggregate aggregateApprovedErc20(@Param("from") OffsetDateTime from,
			@Param("to") OffsetDateTime to);
}
