package com.reelshort.backend.content;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentVideoCacheRepository extends JpaRepository<ContentVideoCache, UUID> {

	Optional<ContentVideoCache> findByBookIdAndEpisodeNumAndFilteredTitleAndChapterId(String bookId, int episodeNum,
			String filteredTitle, String chapterId);

	Optional<ContentVideoCache> findByBookIdAndEpisodeNumAndFilteredTitleAndChapterIdAndLocale(String bookId,
			int episodeNum, String filteredTitle, String chapterId, ContentLocale locale);
}
