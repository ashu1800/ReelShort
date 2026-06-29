package com.reelshort.backend.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class SocialRepositoryTests {

	@Autowired
	private LikeRepository likeRepository;

	@Autowired
	private FavoriteRepository favoriteRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Test
	void likeUserBookCombinationIsUnique() {
		UUID userId = UUID.randomUUID();
		likeRepository.saveAndFlush(Like.create(userId, "book-unique"));

		assertThatThrownBy(() -> likeRepository.saveAndFlush(Like.create(userId, "book-unique")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void favoriteUserBookCombinationIsUnique() {
		UUID userId = UUID.randomUUID();
		favoriteRepository.saveAndFlush(
				Favorite.create(userId, "book-fav", "Title", "filtered", "http://cover", 10));

		assertThatThrownBy(() -> favoriteRepository.saveAndFlush(
				Favorite.create(userId, "book-fav", "Title", "filtered", "http://cover", 10)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void countsByBookAggregateAcrossUsers() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		likeRepository.saveAndFlush(Like.create(a, "book-count"));
		likeRepository.saveAndFlush(Like.create(b, "book-count"));

		assertThat(likeRepository.countByBookId("book-count")).isEqualTo(2);
		assertThat(likeRepository.existsByUserIdAndBookId(a, "book-count")).isTrue();
	}

	@Test
	void commentsListNewestFirst() {
		String bookId = "book-comments";
		commentRepository.saveAndFlush(Comment.create(UUID.randomUUID(), "alice", bookId, "first"));
		commentRepository.saveAndFlush(Comment.create(UUID.randomUUID(), "bob", bookId, "second"));

		assertThat(commentRepository.findByBookIdOrderByCreatedAtDesc(bookId))
				.extracting(Comment::username)
				.containsExactly("bob", "alice");
	}

	@Test
	void favoritesListedByUserNewestFirst() {
		UUID userId = UUID.randomUUID();
		favoriteRepository.saveAndFlush(Favorite.create(userId, "book-a", "A", "a", null, 5));
		favoriteRepository.saveAndFlush(Favorite.create(userId, "book-b", "B", "b", null, 6));

		assertThat(favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId))
				.extracting(Favorite::bookId)
				.containsExactly("book-b", "book-a");
	}
}
