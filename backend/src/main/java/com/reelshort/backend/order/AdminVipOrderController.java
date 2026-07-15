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
import com.reelshort.backend.admin.CurrentAdmin;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/admin/vip/orders")
public class AdminVipOrderController {

	private final VipOrderService vipOrderService;

	public AdminVipOrderController(VipOrderService vipOrderService) {
		this.vipOrderService = vipOrderService;
	}

	@GetMapping
	@RequireAdminPermission(AdminPermissions.ORDER_READ)
	public ApiResponse<List<VipOrderResponse>> list(HttpServletRequest request) {
		return ApiResponse.success(vipOrderService.allOrders().stream().map(VipOrderResponse::from).toList(), requestId(request));
	}

	@PostMapping("/{orderId}/confirm")
	@RequireAdminPermission(AdminPermissions.ORDER_READ)
	public ApiResponse<VipOrderResponse> confirm(CurrentAdmin currentAdmin, @PathVariable UUID orderId,
			@Valid @RequestBody VipConfirmRequest confirmRequest, HttpServletRequest request) {
		return ApiResponse.success(VipOrderResponse.from(vipOrderService.confirm(orderId, confirmRequest.txHash(), currentAdmin.username())),
				requestId(request));
	}

	@PostMapping("/{orderId}/reject")
	@RequireAdminPermission(AdminPermissions.ORDER_READ)
	public ApiResponse<VipOrderResponse> reject(CurrentAdmin currentAdmin, @PathVariable UUID orderId,
			HttpServletRequest request) {
		return ApiResponse.success(VipOrderResponse.from(vipOrderService.reject(orderId, currentAdmin.username())), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}

	public record VipConfirmRequest(@NotBlank @Size(max = 128) String txHash) {
	}
}
