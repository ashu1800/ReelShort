package com.reelshort.backend.content;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.CurrentAdmin;
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
	public ApiResponse<ContentCacheStatusResponse> status(HttpServletRequest request) {
		return ApiResponse.success(contentCacheService.status(), requestId(request));
	}

	@PostMapping("/shelves/{shelfType}/refresh")
	public ApiResponse<List<ContentBook>> refreshShelf(CurrentAdmin currentAdmin, @PathVariable String shelfType,
			HttpServletRequest request) {
		ContentShelfType resolvedShelfType = ContentShelfType.fromApiValue(shelfType);
		List<ContentBook> books = contentCacheService.refreshShelf(resolvedShelfType);
		adminAuditService.record(currentAdmin.username(), "CONTENT_CACHE_REFRESHED", "CONTENT_SHELF",
				targetId(resolvedShelfType), "Refreshed content shelf " + resolvedShelfType.apiValue());
		return ApiResponse.success(books, requestId(request));
	}

	private UUID targetId(ContentShelfType shelfType) {
		return UUID.nameUUIDFromBytes(shelfType.apiValue().getBytes(StandardCharsets.UTF_8));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
