package com.reelshort.backend.social;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	public List<CommentResponse> listComments(String bookId) {
		List<CommentResponse> comments = new ArrayList<>(commentRepository.findByBookIdOrderByCreatedAtDesc(bookId)
				.stream()
				.map(CommentResponse::from)
				.toList());
		if (comments.isEmpty()) {
			comments.addAll(displayFallbacks.comments(bookId));
		}
		return comments;
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
