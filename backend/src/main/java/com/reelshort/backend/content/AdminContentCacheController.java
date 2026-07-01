package com.reelshort.backend.content;

import java.nio.charset.StandardCharsets;
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

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
