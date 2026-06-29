package com.reelshort.backend.social;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.social.SocialDtos.CommentResponse;
import com.reelshort.backend.social.SocialDtos.FavoriteRequest;
import com.reelshort.backend.social.SocialDtos.ToggleResponse;

/**
 * 社交写操作的事务边界。所有写都在 {@link com.reelshort.backend.system.concurrency.UserActionLocks}
 * 持锁后进入此处，保证同用户的切换/评论串行化与唯一约束安全。
 */
@Service
class SocialTransaction {

	private final LikeRepository likeRepository;
	private final FavoriteRepository favoriteRepository;
	private final CommentRepository commentRepository;

	SocialTransaction(LikeRepository likeRepository, FavoriteRepository favoriteRepository,
			CommentRepository commentRepository) {
		this.likeRepository = likeRepository;
		this.favoriteRepository = favoriteRepository;
		this.commentRepository = commentRepository;
	}

	@Transactional
	public ToggleResponse toggleLike(UUID userId, String bookId) {
		boolean nowLiked;
		if (likeRepository.existsByUserIdAndBookId(userId, bookId)) {
			likeRepository.deleteByUserIdAndBookId(userId, bookId);
			nowLiked = false;
		} else {
			likeRepository.save(Like.create(userId, bookId));
			nowLiked = true;
		}
		return new ToggleResponse(nowLiked, likeRepository.countByBookId(bookId));
	}

	@Transactional
	public ToggleResponse toggleFavorite(UUID userId, String bookId, FavoriteRequest request) {
		java.util.Optional<Favorite> existing = favoriteRepository.findByUserIdAndBookId(userId, bookId);
		boolean nowFavorited;
		if (existing.isPresent()) {
			favoriteRepository.delete(existing.get());
			nowFavorited = false;
		} else {
			favoriteRepository.save(Favorite.create(userId, bookId, request.bookTitle(), request.filteredTitle(),
					request.coverUrl(), request.chapterCount()));
			nowFavorited = true;
		}
		return new ToggleResponse(nowFavorited, favoriteRepository.countByBookId(bookId));
	}

	@Transactional
	public CommentResponse addComment(UUID userId, String username, String bookId, String content) {
		Comment saved = commentRepository.save(Comment.create(userId, username, bookId, content));
		return CommentResponse.from(saved);
	}
}
