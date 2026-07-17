package com.reelshort.backend.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.security.SecureTokenComparator;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/internal/payments/recharge/callback")
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentCallbackController {

	private static final String CALLBACK_SECRET_HEADER = "X-Payment-Callback-Secret";

	private final PaymentProperties paymentProperties;
	private final PaymentCallbackService paymentCallbackService;

	public PaymentCallbackController(PaymentProperties paymentProperties, PaymentCallbackService paymentCallbackService) {
		this.paymentProperties = paymentProperties;
		this.paymentCallbackService = paymentCallbackService;
	}

	@PostMapping
	public ApiResponse<PaymentCallbackResponse> callback(
			@RequestHeader(name = CALLBACK_SECRET_HEADER, required = false) String secret,
			@Valid @RequestBody PaymentCallbackRequest request, HttpServletRequest httpRequest) {
		if (!SecureTokenComparator.equals(paymentProperties.callbackSecret(), secret)) {
			throw new PaymentException(401, "unauthorized");
		}
		return ApiResponse.success(paymentCallbackService.handle(request), requestId(httpRequest));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
