package com.reelshort.backend.content;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
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

/**
 * 后台视频库：片库状态、分页浏览/搜索和一键抓取。
 *
 * <p>旧的货架接口 {@code /api/admin/content/cache/shelves/...} 保持原样，暂不删除。
 */
@RestController
@RequestMapping("/api/admin/content/library")
public class AdminContentLibraryController {

	private static final String AUDIT_TARGET_TYPE = "CONTENT_LIBRARY";

	private final ContentLibraryService contentLibraryService;
	private final AdminAuditService adminAuditService;

	public AdminContentLibraryController(ContentLibraryService contentLibraryService,
			AdminAuditService adminAuditService) {
		this.contentLibraryService = contentLibraryService;
		this.adminAuditService = adminAuditService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_READ)
	public ApiResponse<ContentLibraryStatusResponse> status(HttpServletRequest request) {
		return ApiResponse.success(contentLibraryService.status(), requestId(request));
	}

	@GetMapping("/books")
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_READ)
	public ApiResponse<ContentBookPageResponse> books(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "24") int size,
			@RequestParam(required = false) String query,
			HttpServletRequest request) {
		return ApiResponse.success(contentLibraryService.books(page, size, query), requestId(request));
	}

	@PostMapping("/crawl")
	@RequireAdminPermission(AdminPermissions.CONTENT_CACHE_WRITE)
	public ApiResponse<ContentLibraryCrawlResult> crawl(CurrentAdmin currentAdmin, HttpServletRequest request) {
		ContentLibraryCrawlResult result = contentLibraryService.crawl(ContentRefreshTriggerSource.ADMIN);
		adminAuditService.record(currentAdmin.username(), "CONTENT_LIBRARY_CRAWLED", AUDIT_TARGET_TYPE, targetId(),
				"Crawled content library: " + result.fetchedPages() + " pages, added " + result.added()
						+ " books, total " + result.total());
		return ApiResponse.success(result, requestId(request));
	}

	private UUID targetId() {
		return UUID.nameUUIDFromBytes(ContentLibraryCrawlState.SINGLETON_ID.getBytes(StandardCharsets.UTF_8));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
