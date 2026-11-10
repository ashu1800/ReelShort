package com.reelshort.backend.content;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/content/cache")
public class AdminContentCacheController {

	private final ContentCacheService contentCacheService;
	private final AdminAuditService adminAuditService;

	public AdminContentCacheController(ContentCacheService contentCacheService, AdminAuditService adminAuditService) {
		this.contentCacheService = contentCacheService;
		this.adminAuditService = adminAuditService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_READ)
	public ApiResponse<ContentCacheStatusResponse> status(HttpServletRequest request) {
		return ApiResponse.success(contentCacheService.status(), requestId(request));
	}

	@GetMapping("/shelves/{shelfType}/books")
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_READ)
	public ApiResponse<List<ContentBook>> shelfBooks(@PathVariable String shelfType,
			@RequestParam(required = false) String locale, HttpServletRequest request) {
		ContentShelfType resolvedShelfType = ContentShelfType.fromApiValue(shelfType);
		ContentLocale resolvedLocale = parseLocale(locale);
		List<ContentBook> books = contentCacheService.getShelf(resolvedShelfType, resolvedLocale);
		return ApiResponse.success(books, requestId(request));
	}

	@GetMapping("/books/{bookId}/episodes")
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_READ)
	public ApiResponse<List<ContentEpisode>> episodes(@PathVariable String bookId,
			@RequestParam(required = false) String filteredTitle,
			@RequestParam(required = false) String locale,
			HttpServletRequest request) {
		ContentLocale resolvedLocale = parseLocale(locale);
		String titleToUse = filteredTitle;
		if (titleToUse == null || titleToUse.isBlank()) {
			ContentBook book = contentCacheService.getBook(bookId, resolvedLocale);
			titleToUse = book.filteredTitle();
		}
		List<ContentEpisode> episodes = contentCacheService.getEpisodes(bookId, titleToUse, resolvedLocale);
		// 打开审片抽屉时顺手把简介补上：TwistyTV 的 /browse 卡片本来就不带简介，
		// 详情页才有；一本书只补一次（补完非空即命中缓存）。
		contentCacheService.refreshBookIfDescriptionMissing(bookId, titleToUse, resolvedLocale);
		return ApiResponse.success(episodes, requestId(request));
	}

	@GetMapping("/books/{bookId}/episodes/{episodeNum}/video-probe")
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_READ)
	public ApiResponse<ContentVideo> videoProbe(@PathVariable String bookId,
			@PathVariable int episodeNum,
			@RequestParam(required = false) String filteredTitle,
			@RequestParam(required = false) String chapterId,
			@RequestParam(required = false) String locale,
			HttpServletRequest request) {
		ContentLocale resolvedLocale = parseLocale(locale);
		String titleToUse = filteredTitle;
		if (titleToUse == null || titleToUse.isBlank()) {
			ContentBook book = contentCacheService.getBook(bookId, resolvedLocale);
			titleToUse = book.filteredTitle();
		}
		String chapterToUse = chapterId;
		if (chapterToUse == null || chapterToUse.isBlank()) {
			List<ContentEpisode> episodes = contentCacheService.getEpisodes(bookId, titleToUse, resolvedLocale);
			chapterToUse = episodes.stream()
					.filter(episode -> episode.episode() == episodeNum)
					.findFirst()
					.map(ContentEpisode::chapterId)
					.orElseThrow(() -> new ContentProviderException(404, "episode not found"));
		}
		ContentVideo video = contentCacheService.getVideoUrl(bookId, episodeNum, titleToUse, chapterToUse, resolvedLocale);
		return ApiResponse.success(video, requestId(request));
	}

	@PostMapping("/shelves/{shelfType}/refresh")
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_WRITE)
	public ApiResponse<List<ContentBook>> refreshShelf(CurrentAdmin currentAdmin, @PathVariable String shelfType,
			@RequestParam(required = false) String locale, HttpServletRequest request) {
		ContentShelfType resolvedShelfType = ContentShelfType.fromApiValue(shelfType);
		ContentLocale resolvedLocale = parseLocale(locale);
		List<ContentBook> books = contentCacheService.refreshShelf(resolvedShelfType, resolvedLocale,
				ContentRefreshTriggerSource.ADMIN);
		adminAuditService.record(currentAdmin.username(), "CONTENT_CACHE_REFRESHED", "CONTENT_SHELF",
				targetId(resolvedShelfType), "Refreshed content shelf " + resolvedShelfType.apiValue()
						+ " locale " + resolvedLocale.apiValue());
		return ApiResponse.success(books, requestId(request));
	}

	@PostMapping("/shelves/{shelfType}/refresh-locales")
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_WRITE)
	public ApiResponse<List<ContentShelfRefreshResult>> refreshShelfLocales(CurrentAdmin currentAdmin,
			@PathVariable String shelfType, HttpServletRequest request) {
		ContentShelfType resolvedShelfType = ContentShelfType.fromApiValue(shelfType);
		List<ContentShelfRefreshResult> results = new ArrayList<>();
		for (ContentLocale locale : ContentLocale.values()) {
			try {
				List<ContentBook> books = contentCacheService.refreshShelf(
						resolvedShelfType,
						locale,
						ContentRefreshTriggerSource.ADMIN);
				results.add(ContentShelfRefreshResult.success(resolvedShelfType, locale, books.size()));
			}
			catch (RuntimeException exception) {
				results.add(ContentShelfRefreshResult.failed(resolvedShelfType, locale, errorMessage(exception)));
			}
		}
		long successCount = results.stream().filter(result -> "SUCCESS".equals(result.status())).count();
		adminAuditService.record(currentAdmin.username(), "CONTENT_CACHE_REFRESHED_LOCALES", "CONTENT_SHELF",
				targetId(resolvedShelfType), "Refreshed content shelf " + resolvedShelfType.apiValue()
						+ " for " + successCount + "/" + results.size() + " locales");
		return ApiResponse.success(results, requestId(request));
	}

	private ContentLocale parseLocale(String locale) {
		try {
			return ContentLocale.fromApiValue(locale);
		}
		catch (IllegalArgumentException exception) {
			throw new ContentProviderException(400, "unsupported locale");
		}
	}

	private UUID targetId(ContentShelfType shelfType) {
		return UUID.nameUUIDFromBytes(shelfType.apiValue().getBytes(StandardCharsets.UTF_8));
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
				? "refresh failed"
				: exception.getMessage();
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
