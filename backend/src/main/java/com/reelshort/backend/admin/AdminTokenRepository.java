package com.reelshort.backend.admin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminTokenRepository extends JpaRepository<AdminToken, UUID> {

	Optional<AdminToken> findByTokenHash(String tokenHash);
}
