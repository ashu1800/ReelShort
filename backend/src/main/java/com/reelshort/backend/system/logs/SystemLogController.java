package com.reelshort.backend.system.logs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/system/logs")
@RequireAdminPermission(AdminPermissions.SYSTEM_LOG_READ)
public class SystemLogController {

	private final SystemLogService systemLogService;

	public SystemLogController(SystemLogService systemLogService) {
		this.systemLogService = systemLogService;
	}

	@GetMapping
	public ApiResponse<SystemLogResponse> logs(@RequestParam(required = false) String file,
			@RequestParam(required = false) Integer lines, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(systemLogService.read(file, lines), requestId);
	}
}
