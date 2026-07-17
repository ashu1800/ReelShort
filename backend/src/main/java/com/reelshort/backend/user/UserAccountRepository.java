package com.reelshort.backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	boolean existsByUsername(String username);

	Optional<UserAccount> findByUsername(String username);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM UserAccount u WHERE u.id = :id")
	Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);

	long countByStatus(UserStatus status);
}
