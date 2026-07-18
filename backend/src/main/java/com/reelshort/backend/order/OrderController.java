package com.reelshort.backend.order;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.CurrentUser;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/app/orders")
public class OrderController {

	private final RechargeOrderService rechargeOrderService;

	public OrderController(RechargeOrderService rechargeOrderService) {
		this.rechargeOrderService = rechargeOrderService;
	}

	@PostMapping("/recharge")
	public ApiResponse<RechargeOrderResponse> create(CurrentUser currentUser,
			@RequestBody(required = false) Object ignoredRequest, HttpServletRequest httpRequest) {
		throw new OrderException(400, "recharge is not supported");
	}

	@GetMapping
	public ApiResponse<List<RechargeOrderResponse>> orders(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(rechargeOrderService.userOrders(currentUser.userId()), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
