package com.reelshort.backend.order;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/orders")
@RequireAdminPermission(AdminPermissions.ORDER_READ)
public class AdminOrderController {

	private final RechargeOrderService rechargeOrderService;

	public AdminOrderController(RechargeOrderService rechargeOrderService) {
		this.rechargeOrderService = rechargeOrderService;
	}

	@GetMapping
	public ApiResponse<List<RechargeOrderResponse>> orders(HttpServletRequest request) {
		return ApiResponse.success(rechargeOrderService.allOrders(), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
