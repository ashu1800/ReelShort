package com.reelshort.backend.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentBookCacheRepository extends JpaRepository<ContentBookCache, String> {

	Optional<ContentBookCache> findByBookIdAndLocale(String bookId, ContentLocale locale);

	Optional<ContentBookCache> findFirstByLocaleAndChapterCountGreaterThanOrderByUpdatedAtDesc(ContentLocale locale,
			int chapterCount);

	/**
	 * 运营观看任务的候选池：该用户**没有任何观看记录**的英文短剧。
	 * 用 not exists 子查询排除已看过的剧（命中 uk_watch_records_user_book_episode 的 user_id+book_id 前缀），
	 * 避免把用户全部观看记录拼成超长 NOT IN 列表，也避免空集合导致的语法分支。
	 */
	@Query("select b from ContentBookCache b where b.locale = :locale and b.chapterCount > :chapterCount "
			+ "and not exists (select 1 from WatchRecord r where r.userId = :userId and r.bookId = b.bookId) "
			+ "order by b.updatedAt desc")
	List<ContentBookCache> findUnwatchedBooksOrderByUpdatedAtDesc(@Param("locale") ContentLocale locale,
			@Param("chapterCount") int chapterCount, @Param("userId") UUID userId, Pageable pageable);

	@Query("select count(b) from ContentBookCache b where b.locale = :locale and b.chapterCount > :chapterCount "
			+ "and not exists (select 1 from WatchRecord r where r.userId = :userId and r.bookId = b.bookId)")
	long countUnwatchedBooks(@Param("locale") ContentLocale locale, @Param("chapterCount") int chapterCount,
			@Param("userId") UUID userId);

	java.util.List<ContentBookCache> findTop50ByLocaleAndTitleContainingIgnoreCaseOrLocaleAndDescriptionContainingIgnoreCase(
			ContentLocale titleLocale,
			String titleKeyword,
			ContentLocale descriptionLocale,
			String descriptionKeyword);

	default Optional<ContentBookCache> findEnglishByBookId(String bookId) {
		return findByBookIdAndLocale(bookId, ContentLocale.ENGLISH);
	}
}
