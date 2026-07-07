package com.reelshort.backend.wallet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.auth.CurrentUser;
import com.reelshort.backend.auth.SmsSendResponse;
import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/app/wallet")
public class WalletController {

	private final WalletService walletService;

	public WalletController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping
	public ApiResponse<WalletResponse> wallet(CurrentUser currentUser, HttpServletRequest request) {
		return ApiResponse.success(walletService.wallet(currentUser.userId()), requestId(request));
	}

	@PostMapping("/verification/send")
	public ApiResponse<SmsSendResponse> sendVerification(CurrentUser currentUser,
			@Valid @RequestBody WalletVerificationRequest verificationRequest,
			HttpServletRequest request) {
		return ApiResponse.success(walletService.sendVerification(currentUser.userId(), verificationRequest.purpose()),
				requestId(request));
	}

	@PutMapping
	public ApiResponse<WalletResponse> bindOrReplace(CurrentUser currentUser,
			@Valid @RequestBody WalletBindRequest bindRequest,
			HttpServletRequest request) {
		return ApiResponse.success(walletService.bindOrReplace(currentUser.userId(), bindRequest.walletAddress(),
				bindRequest.verificationCode()), requestId(request));
	}

	@PostMapping("/unbind")
	public ApiResponse<WalletResponse> unbind(CurrentUser currentUser,
			@Valid @RequestBody WalletUnbindRequest unbindRequest,
			HttpServletRequest request) {
		return ApiResponse.success(walletService.unbind(currentUser.userId(), unbindRequest.verificationCode()),
				requestId(request));
	}

	@PostMapping("/bank-card")
	public ApiResponse<String> bindBankCard(CurrentUser currentUser, @Valid @RequestBody BankCardBindRequest ignored,
			HttpServletRequest request) {
		walletService.rejectBankCard();
		return ApiResponse.success("unsupported", requestId(request));
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
