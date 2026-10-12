package com.reelshort.backend.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.social.SocialDtos.CommentPageResponse;
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

	@Autowired
	private SocialDisplayFallbacks displayFallbacks;

	@Test
	void toggleLikeFlipsStateAndCounts() {
		UUID userId = UUID.randomUUID();
		String bookId = "book-like-" + userId;
		long baseline = socialService.likeStatus(userId, bookId).count();
		assertThat(baseline).isPositive();

		ToggleResponse on = socialService.toggleLike(userId, bookId);
		assertThat(on.active()).isTrue();
		assertThat(on.count()).isEqualTo(baseline + 1);

		ToggleResponse status = socialService.likeStatus(userId, bookId);
		assertThat(status.active()).isTrue();
		assertThat(status.count()).isEqualTo(baseline + 1);

		ToggleResponse off = socialService.toggleLike(userId, bookId);
		assertThat(off.active()).isFalse();
		assertThat(off.count()).isEqualTo(baseline);
	}

	@Test
	void toggleFavoriteFlipsStateAndStoresSnapshot() {
		UUID userId = UUID.randomUUID();
		String bookId = "book-fav-" + userId;
		FavoriteRequest request = new FavoriteRequest("Love Story", "love-story", "http://cover", 12);
		long baseline = socialService.favoriteStatus(userId, bookId).count();
		assertThat(baseline).isPositive();

		ToggleResponse on = socialService.toggleFavorite(userId, bookId, request);
		assertThat(on.active()).isTrue();
		assertThat(on.count()).isEqualTo(baseline + 1);

		List<FavoriteBookResponse> favorites = socialService.myFavorites(userId);
		assertThat(favorites).hasSize(1);
		assertThat(favorites.get(0).bookId()).isEqualTo(bookId);
		assertThat(favorites.get(0).chapterCount()).isEqualTo(12);

		ToggleResponse off = socialService.toggleFavorite(userId, bookId, request);
		assertThat(off.active()).isFalse();
		assertThat(off.count()).isEqualTo(baseline);
		assertThat(socialService.myFavorites(userId)).isEmpty();
	}

	@Test
	void addCommentPersistsAndLists() {
		UUID userId = UUID.randomUUID();
		String bookId = "book-comment-" + userId;
		CommentResponse first = socialService.addComment(userId, "alice-" + userId, bookId, "hello");
		CommentResponse second = socialService.addComment(userId, "alice-" + userId, bookId, "world");

		assertThat(first.content()).isEqualTo("hello");
		List<CommentResponse> list = socialService.listComments(bookId, 0, 20).comments();
		assertThat(list).extracting(CommentResponse::content).startsWith("world", "hello");
		// 伪评论只补足展示密度，单页永远不超过请求的 size，也不会少于真实评论条数。
		assertThat(list).hasSizeBetween(2, 20);
		assertThat(commentRepository.countByBookId(bookId)).isEqualTo(2);
	}

	@Test
	void emptyBookReturnsStableDisplayFallbacks() {
		UUID userId = UUID.randomUUID();
		String bookId = "book-empty-" + userId;

		ToggleResponse like = socialService.likeStatus(userId, bookId);
		ToggleResponse favorite = socialService.favoriteStatus(userId, bookId);
		List<CommentResponse> first = socialService.listComments(bookId, 0, 20).comments();
		List<CommentResponse> second = socialService.listComments(bookId, 0, 20).comments();

		assertThat(like.active()).isFalse();
		assertThat(like.count()).isPositive();
		assertThat(favorite.active()).isFalse();
		assertThat(favorite.count()).isPositive();
		assertThat(first).isNotEmpty();
		assertThat(second).containsExactlyElementsOf(first);
		assertThat(commentRepository.countByBookId(bookId)).isZero();
	}

	@Test
	void commentsArePagedWithCappedSizeAndHasMore() {
		UUID userId = UUID.randomUUID();
		String bookId = findHotBookId();

		CommentPageResponse first = socialService.listComments(bookId, 0, 20);
		CommentPageResponse second = socialService.listComments(bookId, 1, 20);

		assertThat(first.comments()).hasSize(20);
		assertThat(first.page()).isZero();
		assertThat(first.size()).isEqualTo(20);
		assertThat(first.hasMore()).isTrue();
		assertThat(second.comments()).hasSize(20);
		// 分页之间不能重复返回同一条伪评论。
		assertThat(second.comments()).doesNotContainAnyElementsOf(first.comments());
	}

	@Test
	void commentsPageCapsFakeCommentsWithRealCommentsFirst() {
		UUID userId = UUID.randomUUID();
		String bookId = "book-page-real-" + userId;
		socialService.addComment(userId, "bob-" + userId, bookId, "real-1");
		socialService.addComment(userId, "bob-" + userId, bookId, "real-2");

		CommentPageResponse page = socialService.listComments(bookId, 0, 5);

		assertThat(page.comments()).hasSize(5);
		assertThat(page.comments()).extracting(CommentResponse::content).startsWith("real-2", "real-1");
		assertThat(commentRepository.countByBookId(bookId)).isEqualTo(2);
	}

	@Test
	void commentsPageOutOfRangeReturnsEmptyWithoutMore() {
		String bookId = findHotBookId();

		CommentPageResponse page = socialService.listComments(bookId, 10_000, 20);

		assertThat(page.comments()).isEmpty();
		assertThat(page.hasMore()).isFalse();
	}

	@Test
	void commentsPageForBookWithoutRealCommentsIsBoundedByRequestedSize() {
		String bookId = "book-cold-page";

		CommentPageResponse page = socialService.listComments(bookId, 0, 50);
		CommentPageResponse beyondLast = socialService.listComments(bookId, 1, 50);

		// 伪评论条数按热度分档，单页永远不会超过请求的 size，超界页返回空且没有更多。
		assertThat(page.comments()).hasSizeBetween(1, 50);
		assertThat(beyondLast.comments()).isEmpty();
		assertThat(beyondLast.hasMore()).isFalse();
	}

	@Test
	void commentsPageIsStablePerPage() {
		String bookId = findHotBookId();

		CommentPageResponse first = socialService.listComments(bookId, 0, 10);
		CommentPageResponse second = socialService.listComments(bookId, 0, 10);

		assertThat(second.comments()).containsExactlyElementsOf(first.comments());
	}

	@Test
	void hotBookUsesLargerDisplayFallbacks() {
		String hotBookId = findHotBookId();

		ToggleResponse like = socialService.likeStatus(UUID.randomUUID(), hotBookId);
		ToggleResponse favorite = socialService.favoriteStatus(UUID.randomUUID(), hotBookId);
		List<CommentResponse> comments = socialService.listComments(hotBookId, 0, 50).comments();

		assertThat(like.count()).isGreaterThanOrEqualTo(1_000);
		assertThat(favorite.count()).isGreaterThanOrEqualTo(120);
		assertThat(comments).hasSize(50);
		assertThat(displayFallbacks.hotnessTier(hotBookId)).isEqualTo(SocialDisplayFallbacks.HotnessTier.HOT);
	}

	@Test
	void fallbackCommentsCanBeShuffledWithoutChangingTheCommentSet() {
		String bookId = "book-shuffle";

		List<CommentResponse> first = displayFallbacks.comments(bookId, 101L);
		List<CommentResponse> second = displayFallbacks.comments(bookId, 202L);

		assertThat(first).isNotEmpty();
		assertThat(second).containsExactlyInAnyOrderElementsOf(first);
		assertThat(second).isNotEqualTo(first);
	}

	@Test
	void fallbackCommentUsernamesDoNotUseNumericSuffixes() {
		String hotBookId = findHotBookId();

		assertThat(displayFallbacks.comments(hotBookId, 101L))
				.extracting(CommentResponse::username)
				.allSatisfy(username -> assertThat(username).doesNotContainPattern("\\d"));
	}

	private String findHotBookId() {
		for (int index = 0; index < 10_000; index++) {
			String bookId = "book-hot-" + index;
			if (displayFallbacks.hotnessTier(bookId) == SocialDisplayFallbacks.HotnessTier.HOT) {
				return bookId;
			}
		}
		throw new AssertionError("No hot fallback book id found");
	}
}
