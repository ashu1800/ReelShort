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
	public ApiResponse<VipOrder> createOrder(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(vipOrderService.create(currentUser.userId()), requestId(request));
	}

	@GetMapping("/orders")
	public ApiResponse<List<VipOrder>> myOrders(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(vipOrderService.userOrders(currentUser.userId()), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
