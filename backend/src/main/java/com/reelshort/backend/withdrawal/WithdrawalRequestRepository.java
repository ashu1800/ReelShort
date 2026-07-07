package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

	List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

	long countByUserId(UUID userId);

	List<WithdrawalRequest> findAllByOrderByCreatedAtDesc();
}
