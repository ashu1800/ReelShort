package com.reelshort.backend.content;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentEpisodeRuntimeCacheRepository extends JpaRepository<ContentEpisodeRuntimeCache, UUID> {

	Optional<ContentEpisodeRuntimeCache> findByBookIdAndEpisodeNumAndChapterId(String bookId, int episodeNum,
			String chapterId);
}
