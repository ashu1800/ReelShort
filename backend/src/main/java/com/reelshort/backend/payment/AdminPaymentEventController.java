package com.reelshort.backend.payment;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.RequireAdminPermission;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/payments/events")
@RequireAdminPermission(AdminPermissions.PAYMENT_EVENT_READ)
public class AdminPaymentEventController {

	private final PaymentEventService paymentEventService;

	public AdminPaymentEventController(PaymentEventService paymentEventService) {
		this.paymentEventService = paymentEventService;
	}

	@GetMapping
	public ApiResponse<List<PaymentEventResponse>> events(
			@RequestParam(required = false) PaymentEventStatus status,
			@RequestParam(required = false) String orderNo,
			@RequestParam(required = false) String paymentChannel,
			HttpServletRequest request) {
		return ApiResponse.success(paymentEventService.events(status, orderNo, paymentChannel), requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
