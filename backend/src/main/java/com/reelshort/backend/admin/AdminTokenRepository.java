package com.reelshort.backend.admin;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AdminTokenRepository extends JpaRepository<AdminToken, UUID> {

	Optional<AdminToken> findByTokenHash(String tokenHash);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			delete from AdminToken token
			where token.expiresAt < :cutoff
			   or token.revokedAt < :cutoff
			""")
	int deleteExpiredOrRevokedBefore(OffsetDateTime cutoff);
}
