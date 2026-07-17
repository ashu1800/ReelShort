package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.admin.AdminUserStatus;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.system.concurrency.UserActionLocks;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.system.security.TotpService;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.wallet.UserWalletRepository;

class WithdrawalAdminServiceTests {

	private final WithdrawalRequestRepository withdrawalRepository = mock(WithdrawalRequestRepository.class);
	private final UserWalletRepository walletRepository = mock(UserWalletRepository.class);
	private final PointAccountRepository pointAccountRepository = mock(PointAccountRepository.class);
	private final SystemConfigService configService = mock(SystemConfigService.class);
	private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
	private final TotpService totpService = mock(TotpService.class);
	private final AdminUserRepository adminRepository = mock(AdminUserRepository.class);
	private final WithdrawalPayoutCoordinator payoutCoordinator = mock(WithdrawalPayoutCoordinator.class);
	private final WithdrawalPayoutAttemptRepository attemptRepository = mock(WithdrawalPayoutAttemptRepository.class);
	private final AdminAuditService auditService = mock(AdminAuditService.class);
	private final EthereumProperties ethereumProperties = new EthereumProperties();
	private final TronProperties tronProperties = new TronProperties();
	private WithdrawalService service;

	@BeforeEach
	void setUp() {
		service = new WithdrawalService(withdrawalRepository, walletRepository, pointAccountRepository,
				configService, new UserActionLocks(), userRepository, totpService, adminRepository,
				payoutCoordinator, attemptRepository, ethereumProperties, tronProperties, auditService);
	}

	@Test
	void failedBatchItemAuditsStablePayoutFactsWithoutExceptionDetail() {
		UUID adminId = UUID.randomUUID();
		UUID withdrawalId = UUID.randomUUID();
		AdminUser admin = AdminUser.create("operator", "hash", AdminUserStatus.ACTIVE);
		admin.enableTotp("secret");
		WithdrawalRequest withdrawal = mock(WithdrawalRequest.class);
		when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(totpService.verify("secret", "123456")).thenReturn(true);
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(withdrawal.network()).thenReturn("ERC20");
		when(withdrawal.usdtAmount()).thenReturn(new BigDecimal("12.5"));
		when(payoutCoordinator.prepareAndBroadcast(eq(withdrawalId), eq("eth-key"), any()))
				.thenThrow(new WithdrawalException(503, "rpc detail that must not enter audit"));

		BatchWithdrawalResponse result = service.batchApprove(List.of(withdrawalId), null, "eth-key",
				"123456", adminId);

		assertThat(result.succeeded()).isZero();
		assertThat(result.failed()).isEqualTo(1);
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
				withdrawalId, "network=ERC20,amount=12.5,status=FAILED");
	}

	@Test
	void previewUsesConfiguredPublicAddressesAndPreservesSelectedOrder() {
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		WithdrawalRequest first = withdrawal(firstId, "TRC20", "TReceiver", "2.5", WithdrawalStatus.PENDING);
		WithdrawalRequest second = withdrawal(secondId, "ERC20", "0xReceiver", "3.5", WithdrawalStatus.REJECTED);
		tronProperties.setHotWalletAddress(" THotWallet ");
		ethereumProperties.setHotWalletAddress(" 0xHotWallet ");
		when(withdrawalRepository.findAllById(List.of(firstId, secondId))).thenReturn(List.of(second, first));

		BatchWithdrawalPreviewResponse response = service.batchPreview(List.of(firstId, secondId));

		assertThat(response.tronHotWalletAddress()).isEqualTo("THotWallet");
		assertThat(response.ethHotWalletAddress()).isEqualTo("0xHotWallet");
		assertThat(response.totalUsdt()).isEqualTo("2.5");
		assertThat(response.items()).extracting(BatchWithdrawalPreviewResponse.PreviewItem::withdrawalId)
				.containsExactly(firstId.toString(), secondId.toString());
		assertThat(response.items()).extracting(BatchWithdrawalPreviewResponse.PreviewItem::status)
				.containsExactly(WithdrawalStatus.PENDING, WithdrawalStatus.REJECTED);
	}

	@Test
	void batchResponseDoesNotEchoUnexpectedExceptionDetails() {
		UUID adminId = UUID.randomUUID();
		UUID withdrawalId = UUID.randomUUID();
		AdminUser admin = AdminUser.create("operator", "hash", AdminUserStatus.ACTIVE);
		admin.enableTotp("secret");
		WithdrawalRequest withdrawal = mock(WithdrawalRequest.class);
		when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(totpService.verify("secret", "123456")).thenReturn(true);
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(withdrawal.network()).thenReturn("ERC20");
		when(withdrawal.usdtAmount()).thenReturn(BigDecimal.ONE);
		when(payoutCoordinator.prepareAndBroadcast(eq(withdrawalId), eq("eth-key"), any()))
				.thenThrow(new IllegalStateException("privateKey=must-never-leak"));

		BatchWithdrawalResponse result = service.batchApprove(List.of(withdrawalId), null, "eth-key",
				"123456", adminId);

		assertThat(result.items().get(0).errorMessage()).isEqualTo("payout processing failed");
		assertThat(result.errorMessage()).isEqualTo("payout processing failed");
	}

	@Test
	void previewRejectsDuplicateWithdrawalIds() {
		UUID withdrawalId = UUID.randomUUID();

		assertThatThrownBy(() -> service.batchPreview(List.of(withdrawalId, withdrawalId)))
				.isInstanceOf(com.reelshort.backend.admin.AdminException.class)
				.hasMessage("duplicate withdrawal ids are not allowed");
	}

	private WithdrawalRequest withdrawal(UUID id, String network, String address, String amount,
			WithdrawalStatus status) {
		WithdrawalRequest request = mock(WithdrawalRequest.class);
		when(request.id()).thenReturn(id);
		when(request.userId()).thenReturn(UUID.randomUUID());
		when(request.network()).thenReturn(network);
		when(request.walletAddress()).thenReturn(address);
		when(request.usdtAmount()).thenReturn(new BigDecimal(amount));
		when(request.status()).thenReturn(status);
		return request;
	}
}
