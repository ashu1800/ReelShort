package com.reelshort.backend.social;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

	Optional<Favorite> findByUserIdAndBookId(UUID userId, String bookId);

	boolean existsByUserIdAndBookId(UUID userId, String bookId);

	long deleteByUserIdAndBookId(UUID userId, String bookId);

	long countByBookId(String bookId);

	List<Favorite> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
