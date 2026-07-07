package com.reelshort.backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	boolean existsByUsername(String username);

	boolean existsByPhoneE164(String phoneE164);

	Optional<UserAccount> findByUsername(String username);

	Optional<UserAccount> findByPhoneE164(String phoneE164);

	long countByStatus(UserStatus status);
}
