package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.admin.RequireAdminPermissions;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/withdrawals")
public class AdminWithdrawalController {

	private final WithdrawalService withdrawalService;
	private final WithdrawalStatsService withdrawalStatsService;

	public AdminWithdrawalController(WithdrawalService withdrawalService,
			WithdrawalStatsService withdrawalStatsService) {
		this.withdrawalService = withdrawalService;
		this.withdrawalStatsService = withdrawalStatsService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_READ)
	public ApiResponse<List<WithdrawalResponse>> withdrawals(HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.adminWithdrawals(), requestId(request));
	}

	@PostMapping("/{withdrawalId}/approve")
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_WRITE)
	public ApiResponse<WithdrawalResponse> approve(@PathVariable UUID withdrawalId, HttpServletRequest request) {
		throw new AdminException(410, "automatic payout is disabled");
	}

	@GetMapping("/stats")
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_READ)
	public ApiResponse<WithdrawalStatsResponse> stats(
			@RequestParam(defaultValue = "TODAY") String range,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate,
			HttpServletRequest request) {
		WithdrawalStatsRange parsed;
		try {
			parsed = WithdrawalStatsRange.valueOf(range.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (RuntimeException exception) {
			throw new AdminException(400, "unsupported withdrawal stats range");
		}
		if (parsed == WithdrawalStatsRange.CUSTOM) {
			LocalDate parsedFromDate = parseStatsDate(fromDate);
			LocalDate parsedToDate = parseStatsDate(toDate);
			if (parsedToDate.isBefore(parsedFromDate)) {
				throw new AdminException(400, "custom withdrawal stats end date must not be before start date");
			}
			return ApiResponse.success(withdrawalStatsService.stats(parsedFromDate, parsedToDate), requestId(request));
		}
		return ApiResponse.success(withdrawalStatsService.stats(parsed), requestId(request));
	}

	@PostMapping("/{withdrawalId}/manual-confirm")
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_WRITE)
	public ApiResponse<WithdrawalResponse> manualConfirm(CurrentAdmin currentAdmin,
			@PathVariable UUID withdrawalId, HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.manualConfirm(withdrawalId, currentAdmin.username()),
				requestId(request));
	}

	@PostMapping("/{withdrawalId}/reject")
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_WRITE)
	public ApiResponse<WithdrawalResponse> reject(CurrentAdmin currentAdmin, @PathVariable UUID withdrawalId,
			@Valid @RequestBody WithdrawalRejectRequest rejectRequest, HttpServletRequest request) {
		return ApiResponse.success(withdrawalService.reject(withdrawalId, rejectRequest.reason(),
				currentAdmin.username()), requestId(request));
	}

	@PostMapping("/batch-preview")
	@RequireAdminPermissions({ AdminPermissions.WITHDRAWAL_READ, AdminPermissions.WITHDRAWAL_WRITE })
	public ApiResponse<BatchWithdrawalPreviewResponse> batchPreview(
			@Valid @RequestBody BatchWithdrawalPreviewRequest previewRequest, HttpServletRequest request) {
		throw new AdminException(410, "automatic payout is disabled");
	}

	@PostMapping("/batch-approve")
	@RequireAdminPermission(AdminPermissions.WITHDRAWAL_WRITE)
	public ApiResponse<BatchWithdrawalResponse> batchApprove(HttpServletRequest request) {
		throw new AdminException(410, "automatic payout is disabled");
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}

	private LocalDate parseStatsDate(String value) {
		if (value == null || value.isBlank()) {
			throw new AdminException(400, "custom withdrawal stats dates are required");
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException exception) {
			throw new AdminException(400, "custom withdrawal stats dates must use yyyy-MM-dd");
		}
	}
}
