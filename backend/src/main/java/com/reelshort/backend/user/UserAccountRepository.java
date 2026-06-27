package com.reelshort.backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	boolean existsByUsername(String username);

	Optional<UserAccount> findByUsername(String username);

	long countByStatus(UserStatus status);
}
