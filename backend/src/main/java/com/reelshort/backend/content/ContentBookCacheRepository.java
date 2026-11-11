package com.reelshort.backend.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentBookCacheRepository extends JpaRepository<ContentBookCache, String> {

	Optional<ContentBookCache> findByBookIdAndLocale(String bookId, ContentLocale locale);

	/**
	 * 首页推荐随机抽样：片库是持续增长的，整表下发会越来越大，每次只随机取一批。
	 *
	 * <p>native 查询里直接写 `random()`，H2（PostgreSQL 模式）与生产 PostgreSQL 都支持；
	 * locale 传枚举名字符串（库里 @Enumerated(STRING) 存的是 ENGLISH）。
	 */
	@Query(value = "select * from content_book_cache where locale = :locale order by random() limit :limit",
			nativeQuery = true)
	List<ContentBookCache> findRandom(@Param("locale") String locale, @Param("limit") int limit);

	/** 后台视频库分页（无搜索词）：按首次入库时间倒序。 */
	Page<ContentBookCache> findByLocaleOrderByCreatedAtDesc(ContentLocale locale, Pageable pageable);

	/**
	 * 后台视频库分页 + 搜索：query 为空的分支由上面的派生查询覆盖，这里只处理非空词。
	 *
	 * <p>pattern 由 service 拼成 `%小写词%`；标题、bookId、过滤标题、简介任一命中即可。
	 */
	@Query(value = "select * from content_book_cache where locale = :locale and (lower(title) like :pattern "
			+ "or lower(book_id) like :pattern or lower(filtered_title) like :pattern "
			+ "or lower(description) like :pattern) order by created_at desc",
			countQuery = "select count(*) from content_book_cache where locale = :locale and (lower(title) like :pattern "
					+ "or lower(book_id) like :pattern or lower(filtered_title) like :pattern "
					+ "or lower(description) like :pattern)",
			nativeQuery = true)
	Page<ContentBookCache> searchByLocale(@Param("locale") String locale, @Param("pattern") String pattern,
			Pageable pageable);

	Optional<ContentBookCache> findFirstByLocaleAndChapterCountGreaterThanOrderByUpdatedAtDesc(ContentLocale locale,
			int chapterCount);

	List<ContentBookCache> findByLocaleAndBookIdIn(ContentLocale locale, java.util.Collection<String> bookIds);

	/**
	 * 简介补抓队列：TwistyTV 的 /browse 卡片没有简介，只能按本翻详情页。
	 *
	 * <p>`descriptionCheckedAt` 为空的才会进来——NetShort(`ns_`) 的书上游根本没有简介，
	 * 不记"已尝试"的话每轮都会把这批永远补不到的书重翻一遍。
	 */
	List<ContentBookCache> findByLocaleAndDescriptionAndDescriptionCheckedAtIsNull(ContentLocale locale,
			String description, Pageable pageable);

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
