package com.reelshort.backend.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.points.PointTransactionResponse;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;
import com.reelshort.backend.watch.WatchRecordResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.USER_READ)
	public ApiResponse<List<AdminUserSummaryResponse>> users(HttpServletRequest request) {
		return ApiResponse.success(adminUserService.users(), requestId(request));
	}

	@GetMapping("/{userId}")
	@RequireAdminPermission(AdminPermissions.USER_READ)
	public ApiResponse<AdminUserDetailResponse> detail(@PathVariable UUID userId, HttpServletRequest request) {
		return ApiResponse.success(adminUserService.detail(userId), requestId(request));
	}

	@PostMapping("/{userId}/status")
	@RequireAdminPermission(AdminPermissions.USER_WRITE)
	public ApiResponse<AdminUserDetailResponse> status(CurrentAdmin currentAdmin, @PathVariable UUID userId,
			@Valid @RequestBody AdminUserStatusRequest statusRequest,
			HttpServletRequest request) {
		return ApiResponse.success(adminUserService.changeStatus(currentAdmin.username(), userId, statusRequest.status()),
				requestId(request));
	}

	@PostMapping("/{userId}/points/adjust")
	@RequireAdminPermission(AdminPermissions.POINTS_ADJUST)
	public ApiResponse<AdminUserDetailResponse> adjustPoints(CurrentAdmin currentAdmin, @PathVariable UUID userId,
			@Valid @RequestBody AdminPointAdjustRequest adjustRequest,
			HttpServletRequest request) {
		return ApiResponse.success(adminUserService.adjustPoints(currentAdmin.username(), userId, adjustRequest.amount(),
				adjustRequest.reason()), requestId(request));
	}

	@GetMapping("/{userId}/watch-records")
	@RequireAdminPermission(AdminPermissions.USER_READ)
	public ApiResponse<List<WatchRecordResponse>> watchRecords(@PathVariable UUID userId, HttpServletRequest request) {
		return ApiResponse.success(adminUserService.watchRecords(userId), requestId(request));
	}

	@GetMapping("/{userId}/point-records")
	@RequireAdminPermission(AdminPermissions.USER_READ)
	public ApiResponse<List<PointTransactionResponse>> pointRecords(@PathVariable UUID userId, HttpServletRequest request) {
		return ApiResponse.success(adminUserService.pointRecords(userId), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
