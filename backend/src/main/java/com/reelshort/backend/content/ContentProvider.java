package com.reelshort.backend.content;

import java.util.List;

public interface ContentProvider {

	List<ContentBook> search(String keywords);

	List<ContentBook> getShelf(ContentShelfType shelfType);

	List<ContentEpisode> getEpisodes(String bookId, String filteredTitle);

	ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId);
}
