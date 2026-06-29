package com.reelshort.backend.social;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.CurrentUser;
import com.reelshort.backend.social.SocialDtos.CommentRequest;
import com.reelshort.backend.social.SocialDtos.CommentResponse;
import com.reelshort.backend.social.SocialDtos.FavoriteBookResponse;
import com.reelshort.backend.social.SocialDtos.FavoriteRequest;
import com.reelshort.backend.social.SocialDtos.ToggleResponse;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/app/social")
public class SocialController {

	private final SocialService socialService;

	public SocialController(SocialService socialService) {
		this.socialService = socialService;
	}

	@PostMapping("/books/{bookId}/like")
	public ApiResponse<ToggleResponse> toggleLike(CurrentUser currentUser, @PathVariable @NotBlank String bookId,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(socialService.toggleLike(currentUser.userId(), bookId), requestId(httpRequest));
	}

	@GetMapping("/books/{bookId}/like-status")
	public ApiResponse<ToggleResponse> likeStatus(CurrentUser currentUser, @PathVariable @NotBlank String bookId,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(socialService.likeStatus(currentUser.userId(), bookId), requestId(httpRequest));
	}

	@PostMapping("/books/{bookId}/favorite")
	public ApiResponse<ToggleResponse> toggleFavorite(CurrentUser currentUser, @PathVariable @NotBlank String bookId,
			@Valid @RequestBody FavoriteRequest request, HttpServletRequest httpRequest) {
		return ApiResponse.success(socialService.toggleFavorite(currentUser.userId(), bookId, request),
				requestId(httpRequest));
	}

	@GetMapping("/books/{bookId}/favorite-status")
	public ApiResponse<ToggleResponse> favoriteStatus(CurrentUser currentUser, @PathVariable @NotBlank String bookId,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(socialService.favoriteStatus(currentUser.userId(), bookId), requestId(httpRequest));
	}

	@PostMapping("/books/{bookId}/comments")
	public ApiResponse<CommentResponse> addComment(CurrentUser currentUser, @PathVariable @NotBlank String bookId,
			@Valid @RequestBody CommentRequest request, HttpServletRequest httpRequest) {
		return ApiResponse.success(
				socialService.addComment(currentUser.userId(), currentUser.username(), bookId, request.content()),
				requestId(httpRequest));
	}

	@GetMapping("/books/{bookId}/comments")
	public ApiResponse<List<CommentResponse>> listComments(@PathVariable @NotBlank String bookId,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(socialService.listComments(bookId), requestId(httpRequest));
	}

	@GetMapping("/my/favorites")
	public ApiResponse<List<FavoriteBookResponse>> myFavorites(CurrentUser currentUser,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(socialService.myFavorites(currentUser.userId()), requestId(httpRequest));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
