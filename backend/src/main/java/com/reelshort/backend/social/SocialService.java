package com.reelshort.backend.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.social.SocialDtos.CommentPageResponse;
import com.reelshort.backend.social.SocialDtos.CommentResponse;
import com.reelshort.backend.social.SocialDtos.FavoriteBookResponse;
import com.reelshort.backend.social.SocialDtos.FavoriteRequest;
import com.reelshort.backend.social.SocialDtos.ToggleResponse;
import com.reelshort.backend.system.concurrency.UserActionLocks;

@Service
public class SocialService {

	private final LikeRepository likeRepository;
	private final FavoriteRepository favoriteRepository;
	private final CommentRepository commentRepository;
	private final SocialTransaction socialTransaction;
	private final UserActionLocks userActionLocks;
	private final SocialDisplayFallbacks displayFallbacks;

	public SocialService(LikeRepository likeRepository, FavoriteRepository favoriteRepository,
			CommentRepository commentRepository, SocialTransaction socialTransaction, UserActionLocks userActionLocks,
			SocialDisplayFallbacks displayFallbacks) {
		this.likeRepository = likeRepository;
		this.favoriteRepository = favoriteRepository;
		this.commentRepository = commentRepository;
		this.socialTransaction = socialTransaction;
		this.userActionLocks = userActionLocks;
		this.displayFallbacks = displayFallbacks;
	}

	public ToggleResponse toggleLike(UUID userId, String bookId) {
		return userActionLocks.withUserLock(userId, () -> withLikeFallback(bookId,
				socialTransaction.toggleLike(userId, bookId)));
	}

	@Transactional(readOnly = true)
	public ToggleResponse likeStatus(UUID userId, String bookId) {
		return withLikeFallback(bookId, new ToggleResponse(likeRepository.existsByUserIdAndBookId(userId, bookId),
				likeRepository.countByBookId(bookId)));
	}

	public ToggleResponse toggleFavorite(UUID userId, String bookId, FavoriteRequest request) {
		return userActionLocks.withUserLock(userId,
				() -> withFavoriteFallback(bookId, socialTransaction.toggleFavorite(userId, bookId, request)));
	}

	@Transactional(readOnly = true)
	public ToggleResponse favoriteStatus(UUID userId, String bookId) {
		return withFavoriteFallback(bookId,
				new ToggleResponse(favoriteRepository.existsByUserIdAndBookId(userId, bookId),
						favoriteRepository.countByBookId(bookId)));
	}

	public CommentResponse addComment(UUID userId, String username, String bookId, String content) {
		return userActionLocks.withUserLock(userId,
				() -> socialTransaction.addComment(userId, username, bookId, content));
	}

	@Transactional(readOnly = true)
	public CommentPageResponse listComments(String bookId, int page, int size) {
		int safeSize = Math.max(size, 0);
		int offset = Math.max(page, 0) * safeSize;
		List<CommentResponse> comments = new ArrayList<>(
				commentRepository.findByBookIdOrderByCreatedAtDesc(bookId, PageRequest.of(page, safeSize))
						.stream()
						.map(CommentResponse::from)
						.toList());
		// 伪评论只用于补足展示密度：真实评论始终在前，且按页裁剪，不落库。
		// 伪评论池固定按 bookId 打散，保证翻页之间不重不漏。
		int realTotal = (int) commentRepository.countByBookId(bookId);
		int fallbackTotal = displayFallbacks.commentCount(bookId);
		int fallbackFrom = Math.max(offset - realTotal, 0);
		int fallbackTo = Math.min(Math.max(offset + safeSize - realTotal, 0), fallbackTotal);
		if (fallbackTo > fallbackFrom) {
			comments.addAll(displayFallbacks.commentsPage(bookId, fallbackFrom, fallbackTo - fallbackFrom,
					stableShuffleSeed(bookId)));
		}
		int visibleFallback = Math.min(Math.max(offset + comments.size() - realTotal, 0), fallbackTotal);
		boolean hasMore = offset + comments.size() < realTotal + fallbackTotal && visibleFallback < fallbackTotal;
		return new CommentPageResponse(List.copyOf(comments), hasMore, page, safeSize);
	}

	/** 伪评论展示顺序按 bookId 稳定（同一页多次请求一致，翻页不重不漏）。 */
	private long stableShuffleSeed(String bookId) {
		return Objects.hash("social-fallback-page", bookId);
	}

	@Transactional(readOnly = true)
	public List<FavoriteBookResponse> myFavorites(UUID userId) {
		return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(FavoriteBookResponse::from)
				.toList();
	}

	private ToggleResponse withLikeFallback(String bookId, ToggleResponse real) {
		return new ToggleResponse(real.active(), displayFallbacks.likeCount(bookId, real.count()));
	}

	private ToggleResponse withFavoriteFallback(String bookId, ToggleResponse real) {
		return new ToggleResponse(real.active(), displayFallbacks.favoriteCount(bookId, real.count()));
	}
}
