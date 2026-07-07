package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/withdrawals")
public class AdminWithdrawalController {

	private final WithdrawalService withdrawalService;

	public AdminWithdrawalController(WithdrawalService withdrawalService) {
		this.withdrawalService = withdrawalService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_READ)
	public ApiResponse<List<WithdrawalResponse>> withdrawals(HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.adminWithdrawals(), requestId(request));
	}

	@PostMapping("/{withdrawalId}/approve")
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_WRITE)
	public ApiResponse<WithdrawalResponse> approve(CurrentAdmin currentAdmin, @PathVariable UUID withdrawalId,
			@Valid @RequestBody WithdrawalApprovalRequest approvalRequest, HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.approve(withdrawalId, approvalRequest.txHash(),
				approvalRequest.note(), currentAdmin.username()), requestId(request));
	}

	@PostMapping("/{withdrawalId}/reject")
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_WRITE)
	public ApiResponse<WithdrawalResponse> reject(CurrentAdmin currentAdmin, @PathVariable UUID withdrawalId,
			@Valid @RequestBody WithdrawalRejectRequest rejectRequest, HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.reject(withdrawalId, rejectRequest.reason(),
				currentAdmin.username()), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
