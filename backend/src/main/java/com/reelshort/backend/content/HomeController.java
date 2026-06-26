package com.reelshort.backend.content;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.CurrentUser;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/app/home")
public class HomeController {

	private final ContentCacheService contentCacheService;

	public HomeController(ContentCacheService contentCacheService) {
		this.contentCacheService = contentCacheService;
	}

	@GetMapping("/recommend")
	public ApiResponse<List<ContentBook>> recommend(CurrentUser currentUser, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.getShelf(ContentShelfType.RECOMMEND), requestId);
	}
}
