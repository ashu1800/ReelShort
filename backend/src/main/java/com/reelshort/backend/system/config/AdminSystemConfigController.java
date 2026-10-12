package com.reelshort.backend.system.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.points.PointsService;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/system/configs")
public class AdminSystemConfigController {

	private final SystemConfigService systemConfigService;
	private final AdminAuditService adminAuditService;
	private final PointsService pointsService;

	public AdminSystemConfigController(SystemConfigService systemConfigService, AdminAuditService adminAuditService,
			PointsService pointsService) {
		this.systemConfigService = systemConfigService;
		this.adminAuditService = adminAuditService;
		this.pointsService = pointsService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.SYSTEM_CONFIG_READ)
	public ApiResponse<List<SystemConfigResponse>> configs(HttpServletRequest request) {
		return ApiResponse.success(systemConfigService.configs(), requestId(request));
	}

	@PostMapping("/{configKey}")
	@RequireAdminPermission(AdminPermissions.SYSTEM_CONFIG_WRITE)
	public ApiResponse<SystemConfigResponse> update(CurrentAdmin currentAdmin, @PathVariable String configKey,
			@Valid @RequestBody SystemConfigUpdateRequest updateRequest,
			HttpServletRequest request) {
		try {
			if (configKey.equals(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM)
					|| configKey.equals(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT)) {
				pointsService.snapshotDailyEarningRule();
			}
			SystemConfigResponse response = systemConfigService.update(configKey, updateRequest.value());
			// P2-12: 审计写独立事务，配置更新失败（非法值等）时同样留痕
			adminAuditService.recordIndependent(currentAdmin.username(), "SYSTEM_CONFIG_UPDATED", "SYSTEM_CONFIG",
					targetId(configKey), "Updated " + configKey + " to " + response.value());
			return ApiResponse.success(response, requestId(request));
		}
		catch (RuntimeException exception) {
			adminAuditService.recordIndependent(currentAdmin.username(), "SYSTEM_CONFIG_UPDATE_FAILED", "SYSTEM_CONFIG",
					targetId(configKey), failureSummary(configKey, exception));
			throw exception;
		}
	}

	/**
	 * P2-12: 失败摘要限长（summary 为 varchar(512)），避免审计写入本身失败而掩盖原始异常。
	 */
	private String failureSummary(String configKey, RuntimeException exception) {
		String message = exception.getMessage() == null || exception.getMessage().isBlank()
				? exception.getClass().getSimpleName()
				: exception.getMessage();
		if (message.length() > 200) {
			message = message.substring(0, 200);
		}
		return "status=FAILED updating " + configKey + " (" + message + ")";
	}

	private UUID targetId(String configKey) {
		return UUID.nameUUIDFromBytes(configKey.getBytes(StandardCharsets.UTF_8));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
