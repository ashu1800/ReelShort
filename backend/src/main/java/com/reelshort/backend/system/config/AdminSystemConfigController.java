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
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/system/configs")
public class AdminSystemConfigController {

	private final SystemConfigService systemConfigService;
	private final AdminAuditService adminAuditService;

	public AdminSystemConfigController(SystemConfigService systemConfigService, AdminAuditService adminAuditService) {
		this.systemConfigService = systemConfigService;
		this.adminAuditService = adminAuditService;
	}

	@GetMapping
	public ApiResponse<List<SystemConfigResponse>> configs(HttpServletRequest request) {
		return ApiResponse.success(systemConfigService.configs(), requestId(request));
	}

	@PostMapping("/{configKey}")
	public ApiResponse<SystemConfigResponse> update(CurrentAdmin currentAdmin, @PathVariable String configKey,
			@Valid @RequestBody SystemConfigUpdateRequest updateRequest,
			HttpServletRequest request) {
		SystemConfigResponse response = systemConfigService.update(configKey, updateRequest.value());
		adminAuditService.record(currentAdmin.username(), "SYSTEM_CONFIG_UPDATED", "SYSTEM_CONFIG", targetId(configKey),
				"Updated " + configKey + " to " + response.value());
		return ApiResponse.success(response, requestId(request));
	}

	private UUID targetId(String configKey) {
		return UUID.nameUUIDFromBytes(configKey.getBytes(StandardCharsets.UTF_8));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
