package com.reelshort.backend.points;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PointAccountRepository extends JpaRepository<PointAccount, UUID> {

	Optional<PointAccount> findByUserId(UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from PointAccount account where account.userId = :userId")
	Optional<PointAccount> findByUserIdForUpdate(UUID userId);
}
