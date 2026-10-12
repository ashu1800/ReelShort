package com.reelshort.backend.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 社交模块请求与响应 DTO。集中放一个文件，按 record 复用与 watch 模块一致的 DTO 风格。
 */
public final class SocialDtos {

	private SocialDtos() {
	}

	/** 收藏请求：携带剧的快照字段，便于"我的收藏"列表无需回查内容源即可展示。 */
	public record FavoriteRequest(
			@NotBlank @Size(max = 255) String bookTitle,
			@NotBlank @Size(max = 255) String filteredTitle,
			@Size(max = 512) String coverUrl,
			@jakarta.validation.constraints.Min(0) int chapterCount) {
	}

	/** 新增评论请求。 */
	public record CommentRequest(
			@NotBlank @Size(max = 500) String content) {
	}

	/** 点赞/收藏切换结果。 */
	public record ToggleResponse(boolean active, long count) {
	}

	/** 单条评论响应。 */
	public record CommentResponse(
			java.util.UUID id,
			String username,
			String content,
			String createdAt) {

		public static CommentResponse from(Comment comment) {
			return new CommentResponse(comment.id(), comment.username(), comment.content(),
					comment.createdAt().toString());
		}
	}

	/**
	 * 评论分页响应（P2-16）。
	 *
	 * <p>真实评论按 {@code created_at desc} 分页且始终排在伪评论之前；{@code hasMore} 表示后面还有内容。
	 */
	public record CommentPageResponse(
			java.util.List<CommentResponse> comments,
			boolean hasMore,
			int page,
			int size) {
	}

	/** "我的收藏"列表项，复用剧的快照字段。 */
	public record FavoriteBookResponse(
			String bookId,
			String bookTitle,
			String filteredTitle,
			String coverUrl,
			int chapterCount,
			String createdAt) {

		public static FavoriteBookResponse from(Favorite favorite) {
			return new FavoriteBookResponse(favorite.bookId(), favorite.bookTitle(), favorite.filteredTitle(),
					favorite.coverUrl(), favorite.chapterCount(), favorite.createdAt().toString());
		}
	}
}
