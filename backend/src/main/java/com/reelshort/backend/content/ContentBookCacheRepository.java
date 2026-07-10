package com.reelshort.backend.content;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentBookCacheRepository extends JpaRepository<ContentBookCache, String> {

	Optional<ContentBookCache> findByBookIdAndLocale(String bookId, ContentLocale locale);

	Optional<ContentBookCache> findFirstByLocaleAndChapterCountGreaterThanOrderByUpdatedAtDesc(ContentLocale locale,
			int chapterCount);

	java.util.List<ContentBookCache> findByLocaleAndChapterCountGreaterThanOrderByUpdatedAtDesc(ContentLocale locale,
			int chapterCount);

	java.util.List<ContentBookCache> findTop50ByLocaleAndTitleContainingIgnoreCaseOrLocaleAndDescriptionContainingIgnoreCase(
			ContentLocale titleLocale,
			String titleKeyword,
			ContentLocale descriptionLocale,
			String descriptionKeyword);

	default Optional<ContentBookCache> findEnglishByBookId(String bookId) {
		return findByBookIdAndLocale(bookId, ContentLocale.ENGLISH);
	}
}
