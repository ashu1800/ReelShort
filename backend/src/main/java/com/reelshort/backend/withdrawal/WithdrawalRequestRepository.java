package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

	List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select request from WithdrawalRequest request where request.id = :withdrawalId")
	Optional<WithdrawalRequest> findByIdForUpdate(UUID withdrawalId);

	long countByUserId(UUID userId);

	List<WithdrawalRequest> findAllByOrderByCreatedAtDesc();
}
