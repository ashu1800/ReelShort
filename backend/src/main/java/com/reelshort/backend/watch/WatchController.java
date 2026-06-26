package com.reelshort.backend.watch;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.CurrentUser;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/app/watch")
public class WatchController {

	private final WatchService watchService;

	public WatchController(WatchService watchService) {
		this.watchService = watchService;
	}

	@PostMapping("/progress")
	public ApiResponse<WatchRecordResponse> progress(CurrentUser currentUser,
			@Valid @RequestBody WatchProgressRequest request,
			HttpServletRequest httpRequest) {
		return ApiResponse.success(watchService.reportProgress(currentUser.userId(), request), requestId(httpRequest));
	}

	@GetMapping("/history")
	public ApiResponse<List<WatchRecordResponse>> history(CurrentUser currentUser, HttpServletRequest httpRequest) {
		return ApiResponse.success(watchService.history(currentUser.userId()), requestId(httpRequest));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
