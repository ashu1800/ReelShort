package com.reelshort.backend.content.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 后台维护 ReelShort 上游账号：读取脱敏状态、保存并下发、用已存账号重试登录。
 *
 * <p>密码只进不出：读取接口只回"设过没有"和脱敏邮箱；审计只记脱敏邮箱。
 */
@RestController
@RequestMapping("/api/admin/system/reelshort-account")
public class AdminReelshortAccountController {

	private static final String AUDIT_TARGET = "REELSHORT_ACCOUNT";

	private final ReelshortAccountService accountService;
	private final AdminAuditService adminAuditService;

	public AdminReelshortAccountController(ReelshortAccountService accountService,
			AdminAuditService adminAuditService) {
		this.accountService = accountService;
		this.adminAuditService = adminAuditService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.SYSTEM_CONFIG_READ)
	public ApiResponse<ReelshortAccountView> view(HttpServletRequest request) {
		return ApiResponse.success(accountService.view(), requestId(request));
	}

	@PostMapping
	@RequireAdminPermission(AdminPermissions.SYSTEM_CONFIG_WRITE)
	public ApiResponse<ReelshortAccountSync> save(CurrentAdmin currentAdmin,
			@Valid @RequestBody ReelshortAccountRequest accountRequest, HttpServletRequest request) {
		ReelshortAccountSync result = accountService.save(accountRequest.email(), accountRequest.password());
		adminAuditService.recordIndependent(currentAdmin.username(), "REELSHORT_ACCOUNT_UPDATED", AUDIT_TARGET,
				targetId(result.emailMasked()),
				"Updated ReelShort account " + result.emailMasked() + " (" + result.message() + ")");
		return ApiResponse.success(result, requestId(request));
	}

	/** 不提交新值，直接用已保存的账号请求内容源重新登录一次。 */
	@PostMapping("/test")
	@RequireAdminPermission(AdminPermissions.SYSTEM_CONFIG_WRITE)
	public ApiResponse<ReelshortAccountSync> test(CurrentAdmin currentAdmin, HttpServletRequest request) {
		ReelshortAccountSync result = accountService.resync();
		adminAuditService.recordIndependent(currentAdmin.username(), "REELSHORT_ACCOUNT_TESTED", AUDIT_TARGET,
				targetId(result.emailMasked()),
				"Retried ReelShort login as " + result.emailMasked() + " (" + result.message() + ")");
		return ApiResponse.success(result, requestId(request));
	}

	private UUID targetId(String maskedEmail) {
		return UUID.nameUUIDFromBytes(maskedEmail.getBytes(StandardCharsets.UTF_8));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
