package com.reelshort.backend.content;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
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
	private final VipGateService vipGateService;
	private final SystemConfigService systemConfigService;

	public ContentController(ContentCacheService contentCacheService, VipGateService vipGateService,
			SystemConfigService systemConfigService) {
		this.contentCacheService = contentCacheService;
		this.vipGateService = vipGateService;
		this.systemConfigService = systemConfigService;
	}

	@GetMapping("/search")
	public ApiResponse<List<ContentBook>> search(@RequestParam @NotBlank String keywords,
			@RequestParam(required = false) String locale, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.search(keywords, parseLocale(locale)), requestId);
	}

	@GetMapping("/shelves/{shelfType}")
	public ApiResponse<List<ContentBook>> shelf(@PathVariable @NotBlank String shelfType,
			@RequestParam(required = false) String locale, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.getShelf(ContentShelfType.fromApiValue(shelfType),
				parseLocale(locale)), requestId);
	}

	@GetMapping("/books/{bookId}")
	public ApiResponse<ContentBook> detail(@PathVariable @NotBlank String bookId,
			@RequestParam(required = false) String locale, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(contentCacheService.getBook(bookId, parseLocale(locale)), requestId);
	}

	@GetMapping("/books/{bookId}/episodes")
	public ApiResponse<List<ContentEpisode>> episodes(@PathVariable @NotBlank String bookId,
			@RequestParam @NotBlank String filteredTitle,
			@RequestParam(required = false) String locale,
			CurrentUser currentUser,
			HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		List<ContentEpisode> episodes = contentCacheService.getEpisodes(bookId, filteredTitle, parseLocale(locale));
		if (!vipGateService.isVip(currentUser)) {
			int freeEpisodes = systemConfigService.intValue(SystemConfigRegistry.VIP_FREE_EPISODES);
			episodes = episodes.size() > freeEpisodes ? episodes.subList(0, freeEpisodes) : episodes;
		}
		return ApiResponse.success(episodes, requestId);
	}

	@GetMapping("/books/{bookId}/episodes/{episodeNum}/play")
	public ApiResponse<ContentVideo> play(@PathVariable @NotBlank String bookId,
			@PathVariable @Min(1) int episodeNum,
			@RequestParam @NotBlank String filteredTitle,
			@RequestParam @NotBlank String chapterId,
			@RequestParam(required = false) String locale,
			CurrentUser currentUser,
			HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		if (!vipGateService.isVip(currentUser)) {
			int freeEpisodes = systemConfigService.intValue(SystemConfigRegistry.VIP_FREE_EPISODES);
			if (episodeNum > freeEpisodes) {
				throw new ContentProviderException(403, "VIP required to watch this episode");
			}
		}
		return ApiResponse.success(contentCacheService.getVideoUrl(bookId, episodeNum, filteredTitle, chapterId,
				parseLocale(locale)), requestId);
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
