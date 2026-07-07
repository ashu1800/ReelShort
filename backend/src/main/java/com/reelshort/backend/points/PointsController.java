package com.reelshort.backend.points;

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
@RequestMapping("/api/app/points")
public class PointsController {

	private final PointsService pointsService;

	public PointsController(PointsService pointsService) {
		this.pointsService = pointsService;
	}

	@GetMapping("/account")
	public ApiResponse<PointAccountResponse> account(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(pointsService.account(currentUser.userId()), requestId(request));
	}

	@GetMapping("/records")
	public ApiResponse<List<PointTransactionResponse>> records(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(pointsService.records(currentUser.userId()), requestId(request));
	}

	@PostMapping("/transfers")
	public ApiResponse<PointTransferResponse> transfer(CurrentUser currentUser,
			@Valid @RequestBody PointTransferRequest transferRequest,
			HttpServletRequest request) {
		return ApiResponse.success(pointsService.transfer(currentUser.userId(), transferRequest.recipientAccount(),
				transferRequest.pointAmount()), requestId(request));
	}

	@GetMapping("/transfers")
	public ApiResponse<List<PointTransferResponse>> transfers(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(pointsService.transfers(currentUser.userId()), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
