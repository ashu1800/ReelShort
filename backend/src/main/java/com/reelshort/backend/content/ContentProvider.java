package com.reelshort.backend.content;

import java.util.List;
import java.util.Optional;

public interface ContentProvider {

	List<ContentBook> search(String keywords);

	List<ContentBook> getShelf(ContentShelfType shelfType);

	ContentEpisodesDetail getEpisodesDetail(String bookId, String filteredTitle);

	ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId);

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

