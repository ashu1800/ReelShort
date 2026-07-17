package com.reelshort.backend.withdrawal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface WithdrawalPayoutAttemptRepository extends JpaRepository<WithdrawalPayoutAttempt, UUID> {

	Optional<WithdrawalPayoutAttempt> findByWithdrawalRequestIdAndActiveSlot(UUID withdrawalRequestId,
			String activeSlot);

	Optional<WithdrawalPayoutAttempt> findFirstByWithdrawalRequestIdAndStatusOrderByAttemptNumberDesc(
			UUID withdrawalRequestId, WithdrawalPayoutStatus status);

	Optional<WithdrawalPayoutAttempt> findFirstByWithdrawalRequestIdOrderByAttemptNumberDesc(UUID withdrawalRequestId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select attempt from WithdrawalPayoutAttempt attempt where attempt.id = :attemptId")
	Optional<WithdrawalPayoutAttempt> findByIdForUpdate(UUID attemptId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select attempt from WithdrawalPayoutAttempt attempt "
			+ "where attempt.withdrawalRequestId = :withdrawalId and attempt.activeSlot = 'ACTIVE'")
	Optional<WithdrawalPayoutAttempt> findActiveForUpdate(UUID withdrawalId);

	@Query("select coalesce(max(attempt.attemptNumber), 0) from WithdrawalPayoutAttempt attempt "
			+ "where attempt.withdrawalRequestId = :withdrawalId")
	int maximumAttemptNumber(UUID withdrawalId);

	List<WithdrawalPayoutAttempt> findByStatusInOrderByUpdatedAtAsc(Collection<WithdrawalPayoutStatus> statuses);
}
