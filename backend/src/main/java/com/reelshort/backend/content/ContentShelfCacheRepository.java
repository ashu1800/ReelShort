package com.reelshort.backend.content;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentShelfCacheRepository extends JpaRepository<ContentShelfCache, String> {

	Optional<ContentShelfCache> findByShelfTypeAndLocale(ContentShelfType shelfType, ContentLocale locale);

	default Optional<ContentShelfCache> findById(ContentShelfType shelfType) {
		return findByShelfTypeAndLocale(shelfType, ContentLocale.ENGLISH);
	}
}
