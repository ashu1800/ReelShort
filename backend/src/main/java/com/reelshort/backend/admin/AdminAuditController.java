package com.reelshort.backend.admin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {

	private final AdminAuditService adminAuditService;

	public AdminAuditController(AdminAuditService adminAuditService) {
		this.adminAuditService = adminAuditService;
	}

	@GetMapping
	public ApiResponse<List<AdminAuditLogResponse>> auditLogs(HttpServletRequest request) {
		return ApiResponse.success(adminAuditService.logs(), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
