package com.reelshort.backend.content;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentEpisodeCacheRepository extends JpaRepository<ContentEpisodeCache, UUID> {

	Optional<ContentEpisodeCache> findByBookIdAndFilteredTitle(String bookId, String filteredTitle);

	Optional<ContentEpisodeCache> findByBookIdAndFilteredTitleAndLocale(String bookId, String filteredTitle,
			ContentLocale locale);
}
