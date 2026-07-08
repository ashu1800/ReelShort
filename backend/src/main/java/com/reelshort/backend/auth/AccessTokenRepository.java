package com.reelshort.backend.auth;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccessTokenRepository extends JpaRepository<AccessToken, UUID> {

	@EntityGraph(attributePaths = "user")
	Optional<AccessToken> findByTokenHash(String tokenHash);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update AccessToken token
			   set token.revokedAt = :revokedAt
			 where token.user.id = :userId
			   and token.revokedAt is null
			""")
	int revokeAllActiveByUserId(UUID userId, OffsetDateTime revokedAt);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			delete from AccessToken token
			where token.expiresAt < :cutoff
			   or token.revokedAt < :cutoff
			""")
	int deleteExpiredOrRevokedBefore(OffsetDateTime cutoff);
}
