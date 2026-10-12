package com.reelshort.backend.wallet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.auth.CurrentUser;
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

	@PutMapping
	public ApiResponse<WalletResponse> bindOrReplace(CurrentUser currentUser,
			@Valid @RequestBody WalletBindRequest bindRequest,
			HttpServletRequest request) {
		return ApiResponse.success(
				walletService.bindOrReplace(currentUser.userId(), bindRequest.network(), bindRequest.walletAddress(),
						bindRequest.password()),
				requestId(request));
	}

	@PostMapping("/unbind")
	public ApiResponse<WalletResponse> unbind(CurrentUser currentUser,
			@Valid @RequestBody WalletUnbindRequest unbindRequest, HttpServletRequest request) {
		return ApiResponse.success(walletService.unbind(currentUser.userId(), unbindRequest.password()), requestId(request));
	}

	/**
	 * 银行卡提现入口已彻底下线：不再接收卡号、有效期或 CVV，避免任何敏感卡数据进入服务端与日志。
	 * App 侧仅保留"银行卡暂不支持"的只读说明。
	 */
	@PostMapping("/bank-card")
	public ApiResponse<String> submitBankCard(CurrentUser currentUser, HttpServletRequest request) {
		throw new AdminException(410, "bank card withdrawal is not supported");
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
