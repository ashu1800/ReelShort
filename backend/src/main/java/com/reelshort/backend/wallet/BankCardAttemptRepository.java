package com.reelshort.backend.wallet;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankCardAttemptRepository extends JpaRepository<BankCardAttempt, UUID> {

	Optional<BankCardAttempt> findByUserId(UUID userId);
}
