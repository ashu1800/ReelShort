package com.reelshort.backend.content;

import java.util.List;
import java.util.Optional;

public interface ContentProvider {

	List<ContentBook> search(String keywords, ContentLocale locale);

	List<ContentBook> getShelf(ContentShelfType shelfType, ContentLocale locale);

	ContentEpisodesDetail getEpisodesDetail(String bookId, String filteredTitle, ContentLocale locale);

	ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentLocale locale);

	default List<ContentBook> search(String keywords) {
		return search(keywords, ContentLocale.ENGLISH);
	}

	default List<ContentBook> getShelf(ContentShelfType shelfType) {
		return getShelf(shelfType, ContentLocale.ENGLISH);
	}

	default ContentEpisodesDetail getEpisodesDetail(String bookId, String filteredTitle) {
		return getEpisodesDetail(bookId, filteredTitle, ContentLocale.ENGLISH);
	}

	default ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId) {
		return getVideoUrl(bookId, episodeNum, filteredTitle, chapterId, ContentLocale.ENGLISH);
	}

	/**
	 * 仅返回分集列表，兼容旧调用方。默认实现委托给 {@link #getEpisodesDetail} 并丢弃书籍元信息。
	 */
	default List<ContentEpisode> getEpisodes(String bookId, String filteredTitle) {
		return getEpisodesDetail(bookId, filteredTitle).episodes();
	}

	@SuppressWarnings("unused")
	default Optional<ContentBook> getBookByEpisodes(String bookId, String filteredTitle) {
		return getEpisodesDetail(bookId, filteredTitle).book();
	}
}

