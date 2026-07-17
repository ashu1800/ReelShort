package com.reelshort.backend.withdrawal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * C1 fix: 单笔审批不再接受手动 txHash（安全绕过风险），改为接受链私钥走 approveWithTransfer。
 * 私钥按需提供——根据提现单的 network 选择对应链的私钥。
 */
public record WithdrawalApprovalRequest(
		@Size(max = 128) String tronPrivateKey,
		@Size(max = 128) String ethPrivateKey,
		@NotBlank @Pattern(regexp = "\\d{6}") String totpCode) {
}
