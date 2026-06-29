package com.reelshort.backend.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.social.SocialDtos.CommentResponse;
import com.reelshort.backend.social.SocialDtos.FavoriteBookResponse;
import com.reelshort.backend.social.SocialDtos.FavoriteRequest;
import com.reelshort.backend.social.SocialDtos.ToggleResponse;

@SpringBootTest
class SocialServiceTests {

	@Autowired
	private SocialService socialService;

	@Autowired
	private CommentRepository commentRepository;

	@Test
	void toggleLikeFlipsStateAndCounts() {
		UUID userId = UUID.randomUUID();
		String bookId = "book-like-" + userId;

		ToggleResponse on = socialService.toggleLike(userId, bookId);
		assertThat(on.active()).isTrue();
		assertThat(on.count()).isEqualTo(1);

		ToggleResponse status = socialService.likeStatus(userId, bookId);
		assertThat(status.active()).isTrue();

		ToggleResponse off = socialService.toggleLike(userId, bookId);
		assertThat(off.active()).isFalse();
		assertThat(off.count()).isZero();
	}

	@Test
	void toggleFavoriteFlipsStateAndStoresSnapshot() {
		UUID userId = UUID.randomUUID();
		String bookId = "book-fav-" + userId;
		FavoriteRequest request = new FavoriteRequest("Love Story", "love-story", "http://cover", 12);

		ToggleResponse on = socialService.toggleFavorite(userId, bookId, request);
		assertThat(on.active()).isTrue();

		List<FavoriteBookResponse> favorites = socialService.myFavorites(userId);
		assertThat(favorites).hasSize(1);
		assertThat(favorites.get(0).bookId()).isEqualTo(bookId);
		assertThat(favorites.get(0).chapterCount()).isEqualTo(12);

		ToggleResponse off = socialService.toggleFavorite(userId, bookId, request);
		assertThat(off.active()).isFalse();
		assertThat(socialService.myFavorites(userId)).isEmpty();
	}

	@Test
	void addCommentPersistsAndLists() {
		UUID userId = UUID.randomUUID();
		String bookId = "book-comment-" + userId;
		CommentResponse first = socialService.addComment(userId, "alice-" + userId, bookId, "hello");
		CommentResponse second = socialService.addComment(userId, "alice-" + userId, bookId, "world");

		assertThat(first.content()).isEqualTo("hello");
		List<CommentResponse> list = socialService.listComments(bookId);
		assertThat(list).extracting(CommentResponse::content).containsExactly("world", "hello");
		assertThat(commentRepository.countByBookId(bookId)).isEqualTo(2);
	}
}
