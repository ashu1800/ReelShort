package com.reelshort.backend.content;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/app/content")
public class ContentController {

	private final ContentProvider contentProvider;

	public ContentController(ContentProvider contentProvider) {
		this.contentProvider = contentProvider;
	}

	@GetMapping("/search")
	public ApiResponse<List<ContentBook>> search(@RequestParam String keywords, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentProvider.search(keywords), requestId);
	}
}
