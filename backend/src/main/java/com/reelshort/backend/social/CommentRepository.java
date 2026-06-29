package com.reelshort.backend.social;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

	List<Comment> findByBookIdOrderByCreatedAtDesc(String bookId);

	long countByBookId(String bookId);
}
