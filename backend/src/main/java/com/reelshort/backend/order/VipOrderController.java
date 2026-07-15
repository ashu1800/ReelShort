package com.reelshort.backend.order;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.CurrentUser;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/app/vip")
public class VipOrderController {

	private final VipOrderService vipOrderService;

	public VipOrderController(VipOrderService vipOrderService) {
		this.vipOrderService = vipOrderService;
	}

	@PostMapping("/orders")
	public ApiResponse<VipOrderResponse> createOrder(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(VipOrderResponse.from(vipOrderService.create(currentUser.userId())), requestId(request));
	}

	@GetMapping("/orders")
	public ApiResponse<List<VipOrderResponse>> myOrders(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(vipOrderService.userOrders(currentUser.userId()).stream().map(VipOrderResponse::from).toList(), requestId(request));
	}

	@GetMapping("/orders/latest")
	public ApiResponse<VipOrderResponse> latestOrder(CurrentUser currentUser, HttpServletRequest request) {
		List<VipOrder> orders = vipOrderService.userOrders(currentUser.userId());
		if (orders.isEmpty()) {
			return ApiResponse.success(null, requestId(request));
		}
		return ApiResponse.success(VipOrderResponse.from(orders.get(0)), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
