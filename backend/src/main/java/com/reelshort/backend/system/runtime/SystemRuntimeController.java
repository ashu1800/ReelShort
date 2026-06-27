package com.reelshort.backend.system.runtime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/system/runtime")
@RequireAdminPermission(AdminPermissions.SYSTEM_RUNTIME_READ)
public class SystemRuntimeController {

	private final SystemRuntimeService systemRuntimeService;

	public SystemRuntimeController(SystemRuntimeService systemRuntimeService) {
		this.systemRuntimeService = systemRuntimeService;
	}

	@GetMapping
	public ApiResponse<SystemRuntimeResponse> runtime(HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ApiResponse.success(systemRuntimeService.snapshot(), requestId);
	}
}
