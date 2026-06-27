package com.reelshort.backend.content;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;
import com.reelshort.backend.auth.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/app/content")
@Validated
public class ContentController {

	private final ContentCacheService contentCacheService;

	public ContentController(ContentCacheService contentCacheService) {
		this.contentCacheService = contentCacheService;
	}

	@GetMapping("/search")
	public ApiResponse<List<ContentBook>> search(@RequestParam @NotBlank String keywords, CurrentUser currentUser,
			HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.search(keywords), requestId);
	}

	@GetMapping("/shelves/{shelfType}")
	public ApiResponse<List<ContentBook>> shelf(@PathVariable @NotBlank String shelfType, CurrentUser currentUser,
			HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.getShelf(ContentShelfType.fromApiValue(shelfType)), requestId);
	}

	@GetMapping("/books/{bookId}")
	public ApiResponse<ContentBook> detail(@PathVariable @NotBlank String bookId, CurrentUser currentUser,
			HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.getBook(bookId), requestId);
	}

	@GetMapping("/books/{bookId}/episodes")
	public ApiResponse<List<ContentEpisode>> episodes(@PathVariable @NotBlank String bookId,
			@RequestParam @NotBlank String filteredTitle,
			CurrentUser currentUser,
			HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.getEpisodes(bookId, filteredTitle), requestId);
	}

	@GetMapping("/books/{bookId}/episodes/{episodeNum}/play")
	public ApiResponse<ContentVideo> play(@PathVariable @NotBlank String bookId,
			@PathVariable @Min(1) int episodeNum,
			@RequestParam @NotBlank String filteredTitle,
			@RequestParam @NotBlank String chapterId,
			CurrentUser currentUser,
			HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.getVideoUrl(bookId, episodeNum, filteredTitle, chapterId), requestId);
	}
}
