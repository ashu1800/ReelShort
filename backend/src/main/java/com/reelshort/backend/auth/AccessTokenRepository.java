package com.reelshort.backend.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

	@EntityGraph(attributePaths = "user")
	Optional<AccessToken> findByTokenHash(String tokenHash);
}
