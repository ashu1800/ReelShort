package com.reelshort.backend.content;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.CurrentUser;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/app/home")
public class HomeController {

	private final ContentLibraryService contentLibraryService;
	private final ContentLibraryProperties contentLibraryProperties;

	public HomeController(ContentLibraryService contentLibraryService,
			ContentLibraryProperties contentLibraryProperties) {
		this.contentLibraryService = contentLibraryService;
		this.contentLibraryProperties = contentLibraryProperties;
	}

	@GetMapping("/recommend")
	public ApiResponse<List<ContentBook>> recommend(@RequestParam(required = false) String locale,
			CurrentUser currentUser, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		// 片库只有英文数据；locale 参数保留原有校验语义（非法值仍返回 400）。
		parseLocale(locale);
		// 路径与响应结构保持不变，App 不需要发版：从片库随机抽一批（默认 300 部）。
		List<ContentBook> books = contentLibraryService.homeBooks(contentLibraryProperties.getSampleLimit());
		return ApiResponse.success(books, requestId);
	}

	private ContentLocale parseLocale(String locale) {
		try {
			return ContentLocale.fromApiValue(locale);
		}
		catch (IllegalArgumentException exception) {
			throw new ContentProviderException(400, "unsupported locale");
		}
	}
}
