package com.reelshort.backend.social;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, UUID> {

	Optional<Like> findByUserIdAndBookId(UUID userId, String bookId);

	boolean existsByUserIdAndBookId(UUID userId, String bookId);

	long deleteByUserIdAndBookId(UUID userId, String bookId);

	long countByBookId(String bookId);

	long countByCreatedAtGreaterThanEqual(OffsetDateTime createdAt);
}
