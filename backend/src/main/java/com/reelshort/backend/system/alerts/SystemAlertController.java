package com.reelshort.backend.system.alerts;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.runtime.SystemRuntimeService;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/system/alerts")
public class SystemAlertController {

	private final SystemAlertService systemAlertService;
	private final SystemRuntimeService systemRuntimeService;
	private final AdminAuditService adminAuditService;

	public SystemAlertController(SystemAlertService systemAlertService, SystemRuntimeService systemRuntimeService,
			AdminAuditService adminAuditService) {
		this.systemAlertService = systemAlertService;
		this.systemRuntimeService = systemRuntimeService;
		this.adminAuditService = adminAuditService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.SYSTEM_ALERT_READ)
	public ApiResponse<List<SystemAlertResponse>> alerts(@RequestParam(required = false) SystemAlertStatus status,
			HttpServletRequest request) {
		return ApiResponse.success(systemAlertService.alerts(status), requestId(request));
	}

	@PostMapping("/evaluate")
	@RequireAdminPermission(AdminPermissions.SYSTEM_ALERT_READ)
	public ApiResponse<List<SystemAlertResponse>> evaluate(HttpServletRequest request) {
		return ApiResponse.success(systemAlertService.evaluate(systemRuntimeService.snapshot()), requestId(request));
	}

	@PostMapping("/{alertId}/acknowledge")
	@RequireAdminPermission(AdminPermissions.SYSTEM_ALERT_WRITE)
	public ApiResponse<SystemAlertResponse> acknowledge(@PathVariable UUID alertId, CurrentAdmin currentAdmin,
			HttpServletRequest request) {
		SystemAlertResponse response = systemAlertService.acknowledge(alertId, currentAdmin.username());
		adminAuditService.record(currentAdmin.username(), "SYSTEM_ALERT_ACKNOWLEDGED", "SYSTEM_ALERT", alertId,
				"Acknowledged system alert " + response.alertKey());
		return ApiResponse.success(response, requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
