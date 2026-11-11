package com.reelshort.backend.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {

	private final AdminStatisticsService adminStatisticsService;

	public AdminStatisticsController(AdminStatisticsService adminStatisticsService) {
		this.adminStatisticsService = adminStatisticsService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.DASHBOARD_READ)
	public ApiResponse<AdminStatisticsResponse> statistics(@RequestParam(defaultValue = "all") String range,
			HttpServletRequest request) {
		return ApiResponse.success(adminStatisticsService.statistics(AdminStatisticsRange.from(range)),
				(String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
	}
}
