package com.reelshort.backend.withdrawal;

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
@RequestMapping("/api/app/withdrawals")
public class WithdrawalController {

	private final WithdrawalService withdrawalService;

	public WithdrawalController(WithdrawalService withdrawalService) {
		this.withdrawalService = withdrawalService;
	}

	@GetMapping("/summary")
	public ApiResponse<WithdrawalSummaryResponse> summary(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.summary(currentUser.userId()), requestId(request));
	}

	@GetMapping
	public ApiResponse<List<WithdrawalResponse>> withdrawals(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.userWithdrawals(currentUser.userId()), requestId(request));
	}

	@PostMapping
	public ApiResponse<WithdrawalResponse> create(CurrentUser currentUser,
			@Valid @RequestBody WithdrawalCreateRequest createRequest,
			HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.create(currentUser.userId(), createRequest.pointAmount()),
				requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
