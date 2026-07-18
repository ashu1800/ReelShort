package com.reelshort.backend.order;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.security.TotpService;
import com.reelshort.backend.system.web.RequestIdFilter;
import com.reelshort.backend.withdrawal.WithdrawalException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/admin/vip/orders")
public class AdminVipOrderController {

	private final VipOrderService vipOrderService;
	private final AdminUserRepository adminUserRepository;
	private final TotpService totpService;
	private final AdminAuditService adminAuditService;

	public AdminVipOrderController(VipOrderService vipOrderService, AdminUserRepository adminUserRepository,
			TotpService totpService, AdminAuditService adminAuditService) {
		this.vipOrderService = vipOrderService;
		this.adminUserRepository = adminUserRepository;
		this.totpService = totpService;
		this.adminAuditService = adminAuditService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.ORDER_READ)
	public ApiResponse<List<VipOrderResponse>> list(HttpServletRequest request) {
		return ApiResponse.success(vipOrderService.allOrders().stream().map(VipOrderResponse::from).toList(), requestId(request));
	}

	@PostMapping("/{orderId}/confirm")
	@RequireAdminPermission(AdminPermissions.ORDER_WRITE)
	public ApiResponse<VipOrderResponse> confirm(CurrentAdmin currentAdmin, @PathVariable UUID orderId,
			@Valid @RequestBody VipConfirmRequest confirmRequest, HttpServletRequest request) {
		verifyTotp(currentAdmin, confirmRequest.totpCode(), orderId);
		try {
			VipOrder confirmed = vipOrderService.manualConfirm(orderId, confirmRequest.txHash(), currentAdmin.username());
			return ApiResponse.success(VipOrderResponse.from(confirmed), requestId(request));
		}
		catch (AdminException | WithdrawalException exception) {
			adminAuditService.recordIndependent(currentAdmin.username(), "VIP_ORDER_CONFIRM_FAILED", "VIP_ORDER", orderId,
					"status=CHAIN_VERIFICATION_FAILED");
			throw exception;
		}
	}

	@PostMapping("/{orderId}/reject")
	@RequireAdminPermission(AdminPermissions.ORDER_WRITE)
	public ApiResponse<VipOrderResponse> reject(CurrentAdmin currentAdmin, @PathVariable UUID orderId,
			HttpServletRequest request) {
		try {
			VipOrder rejected = vipOrderService.reject(orderId, currentAdmin.username());
			return ApiResponse.success(VipOrderResponse.from(rejected), requestId(request));
		}
		catch (AdminException exception) {
			adminAuditService.recordIndependent(currentAdmin.username(), "VIP_ORDER_REJECT_FAILED", "VIP_ORDER", orderId,
					"status=FAILED");
			throw exception;
		}
	}

	private void verifyTotp(CurrentAdmin currentAdmin, String code, UUID orderId) {
		AdminUser admin = adminUserRepository.findById(currentAdmin.adminUserId())
				.orElseThrow(() -> new AdminException(404, "admin not found"));
		if (!admin.totpEnabled() || !totpService.verify(admin.totpSecret(), code)) {
			adminAuditService.recordIndependent(currentAdmin.username(), "VIP_ORDER_CONFIRM_FAILED", "VIP_ORDER", orderId,
					"status=TOTP_REJECTED");
			throw new AdminException(403, "2FA verification failed");
		}
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}

	public record VipConfirmRequest(
			@NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}") String txHash,
			@NotBlank @Size(min = 6, max = 6) @Pattern(regexp = "\\d{6}") String totpCode) {
	}
}
