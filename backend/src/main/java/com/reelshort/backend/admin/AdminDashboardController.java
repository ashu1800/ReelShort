package com.reelshort.backend.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

	private final AdminDashboardService adminDashboardService;

	public AdminDashboardController(AdminDashboardService adminDashboardService) {
		this.adminDashboardService = adminDashboardService;
	}

	@GetMapping("/summary")
	@RequireAdminPermission(AdminPermissions.DASHBOARD_READ)
	public ApiResponse<AdminDashboardSummaryResponse> summary(HttpServletRequest request) {
		return ApiResponse.success(adminDashboardService.summary(),
				(String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
	}
}
