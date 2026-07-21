package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

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
	private final PayoutBalancePreflightService balancePreflight = mock(PayoutBalancePreflightService.class);
	private final WithdrawalPayoutAttemptRepository attemptRepository = mock(WithdrawalPayoutAttemptRepository.class);
	private final AdminAuditService auditService = mock(AdminAuditService.class);
	private final EthereumProperties ethereumProperties = new EthereumProperties();
	private final TronProperties tronProperties = new TronProperties();
	private WithdrawalService service;

	@BeforeEach
	void setUp() {
		service = new WithdrawalService(withdrawalRepository, walletRepository, pointAccountRepository,
				configService, new UserActionLocks(), userRepository, totpService, adminRepository,
				payoutCoordinator, balancePreflight, attemptRepository,
				ethereumProperties, tronProperties, new BscProperties(),
				auditService);
	}

	@Test
	void batchBalanceFailureStopsBeforeCreatingAnyPayoutAttempt() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		WithdrawalRequest withdrawal = payoutWithdrawal(withdrawalId);
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		doThrow(new WithdrawalException(409,
				"ERC20 USDT balance insufficient: required=12.5, available=0"))
				.when(balancePreflight).requireSufficient(List.of(withdrawal), null, "eth-key", null);

		assertThatThrownBy(() -> service.batchApprove(
				List.of(withdrawalId), null, "eth-key", null, "123456", adminId))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("balance insufficient");
		verify(payoutCoordinator, never()).prepareAndBroadcast(any(), any(), any());
	}

	@Test
	void batchRejectsPreparedAttemptBeforePayoutExecution() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		WithdrawalRequest withdrawal = payoutWithdrawal(withdrawalId);
		WithdrawalPayoutAttempt attempt = preparedAttempt(withdrawalId);
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(attemptRepository.findFirstByWithdrawalRequestIdOrderByAttemptNumberDesc(withdrawalId))
				.thenReturn(Optional.of(attempt));

		assertThatThrownBy(() -> service.batchApprove(
				List.of(withdrawalId), null, "eth-key", null, "123456", adminId))
				.isInstanceOf(com.reelshort.backend.admin.AdminException.class)
				.hasMessageContaining("not eligible for batch payout");
		verify(balancePreflight, never()).requireSufficient(any(), any(), any(), any());
		verify(payoutCoordinator, never()).prepareAndBroadcast(any(), any(), any());
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
		when(withdrawal.id()).thenReturn(withdrawalId);
		when(withdrawal.status()).thenReturn(WithdrawalStatus.PENDING);
		when(withdrawal.network()).thenReturn("ERC20");
		when(withdrawal.usdtAmount()).thenReturn(new BigDecimal("12.5"));
		when(payoutCoordinator.prepareAndBroadcast(eq(withdrawalId), eq("eth-key"), any()))
				.thenThrow(new WithdrawalException(503, "rpc detail that must not enter audit"));

		BatchWithdrawalResponse result = service.batchApprove(List.of(withdrawalId), null, "eth-key", null,
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
		WithdrawalRequest second = withdrawal(secondId, "ERC20", "0xReceiver", "3.5", WithdrawalStatus.PENDING);
		tronProperties.setHotWalletAddress(" THotWallet ");
		ethereumProperties.setHotWalletAddress(" 0xHotWallet ");
		when(withdrawalRepository.findAllById(List.of(firstId, secondId))).thenReturn(List.of(second, first));
		when(balancePreflight.estimateFees(List.of(first, second))).thenReturn(List.of(
				new PayoutFeeEstimate("TRC20", "TRX", 1, new BigDecimal("100"), "MAXIMUM"),
				new PayoutFeeEstimate("ERC20", "ETH", 1, new BigDecimal("0.002"), "ESTIMATE")));

		BatchWithdrawalPreviewResponse response = service.batchPreview(List.of(firstId, secondId));

		assertThat(response.tronHotWalletAddress()).isEqualTo("THotWallet");
		assertThat(response.ethHotWalletAddress()).isEqualTo("0xHotWallet");
		assertThat(response.totalUsdt()).isEqualTo("6");
		assertThat(response.items()).extracting(BatchWithdrawalPreviewResponse.PreviewItem::withdrawalId)
				.containsExactly(firstId.toString(), secondId.toString());
		assertThat(response.items()).extracting(BatchWithdrawalPreviewResponse.PreviewItem::status)
				.containsExactly(WithdrawalStatus.PENDING, WithdrawalStatus.PENDING);
		assertThat(response.feeEstimates()).containsExactly(
				new BatchWithdrawalPreviewResponse.FeeEstimate("TRC20", "TRX", 1, "100", "MAXIMUM"),
				new BatchWithdrawalPreviewResponse.FeeEstimate("ERC20", "ETH", 1, "0.002", "ESTIMATE"));
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
		when(withdrawal.id()).thenReturn(withdrawalId);
		when(withdrawal.status()).thenReturn(WithdrawalStatus.PENDING);
		when(withdrawal.network()).thenReturn("ERC20");
		when(withdrawal.usdtAmount()).thenReturn(BigDecimal.ONE);
		when(payoutCoordinator.prepareAndBroadcast(eq(withdrawalId), eq("eth-key"), any()))
				.thenThrow(new IllegalStateException("privateKey=must-never-leak"));

		BatchWithdrawalResponse result = service.batchApprove(List.of(withdrawalId), null, "eth-key", null,
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

	@Test
	void failedRetryableAttemptIsReportedAndAuditedAsFailure() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		WithdrawalRequest withdrawal = payoutWithdrawal(withdrawalId);
		WithdrawalPayoutAttempt attempt = preparedAttempt(withdrawalId);
		attempt.markFailedRetryable("RPC_REJECTED", "node rejected transaction");
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(payoutCoordinator.prepareAndBroadcast(withdrawalId, "eth-key", "operator")).thenReturn(attempt);

		BatchWithdrawalResponse result = service.batchApprove(List.of(withdrawalId), null, "eth-key", null,
				"123456", adminId);

		assertThat(result.succeeded()).isZero();
		assertThat(result.failed()).isEqualTo(1);
		assertThat(result.items().get(0).payoutStatus()).isEqualTo("FAILED_RETRYABLE");
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
				withdrawalId, "network=ERC20,amount=12.5,status=FAILED_RETRYABLE,txHash=0xpayout");
	}

	@Test
	void manualReviewAttemptIsNotReportedAsSubmitted() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		WithdrawalRequest withdrawal = payoutWithdrawal(withdrawalId);
		WithdrawalPayoutAttempt attempt = preparedAttempt(withdrawalId);
		attempt.markManualReview("chain state is ambiguous");
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(payoutCoordinator.prepareAndBroadcast(withdrawalId, "eth-key", "operator")).thenReturn(attempt);

		BatchWithdrawalResponse result = service.batchApprove(List.of(withdrawalId), null, "eth-key", null,
				"123456", adminId);

		assertThat(result.succeeded()).isZero();
		assertThat(result.failed()).isEqualTo(1);
		assertThat(result.items().get(0).manualReview()).isTrue();
		assertThat(result.items().get(0).failureReason()).isEqualTo("chain state is ambiguous");
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
				withdrawalId, "network=ERC20,amount=12.5,status=MANUAL_REVIEW,txHash=0xpayout");
	}

	@Test
	void singleApprovalAuditsExplicitlyRejectedAttemptAsFailure() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		WithdrawalRequest withdrawal = payoutWithdrawal(withdrawalId);
		WithdrawalPayoutAttempt attempt = preparedAttempt(withdrawalId);
		attempt.markFailedRetryable("RPC_REJECTED", "node rejected transaction");
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(payoutCoordinator.prepareAndBroadcast(withdrawalId, "eth-key", "operator")).thenReturn(attempt);
		when(attemptRepository.findFirstByWithdrawalRequestIdOrderByAttemptNumberDesc(withdrawalId))
				.thenReturn(Optional.of(attempt));

		WithdrawalResponse response = service.approve(withdrawalId, null, "eth-key", null, "123456", adminId,
				"operator");

		assertThat(response.payoutStatus()).isEqualTo("FAILED_RETRYABLE");
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
				withdrawalId, "network=ERC20,amount=12.5,status=FAILED_RETRYABLE,txHash=0xpayout");
	}

	@Test
	void missingApprovalTargetWritesStableIndependentAudit() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.approve(withdrawalId, null, "eth-key", null, "123456", adminId,
				"operator"))
				.isInstanceOf(com.reelshort.backend.admin.AdminException.class)
				.hasMessage("withdrawal not found");
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
				withdrawalId, "status=NOT_FOUND");
	}

	@Test
	void missingRejectTargetWritesStableIndependentAudit() {
		UUID withdrawalId = UUID.randomUUID();
		when(withdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.reject(withdrawalId, "invalid wallet", "operator"))
				.isInstanceOf(com.reelshort.backend.admin.AdminException.class)
				.hasMessage("withdrawal not found");
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_REJECT_FAILED", "WITHDRAWAL",
				withdrawalId, "status=NOT_FOUND");
	}

	@Test
	void approvalLookupFailureWritesStableIndependentAudit() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		when(withdrawalRepository.findById(withdrawalId))
				.thenThrow(new IllegalStateException("database detail must not enter audit"));

		assertThatThrownBy(() -> service.approve(withdrawalId, null, "eth-key", null, "123456", adminId,
				"operator"))
				.isInstanceOf(IllegalStateException.class);
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
				withdrawalId, "status=LOOKUP_FAILED");
	}

	@Test
	void rejectLookupFailureWritesStableIndependentAudit() {
		UUID withdrawalId = UUID.randomUUID();
		when(withdrawalRepository.findByIdForUpdate(withdrawalId))
				.thenThrow(new IllegalStateException("database detail must not enter audit"));

		assertThatThrownBy(() -> service.reject(withdrawalId, "invalid wallet", "operator"))
				.isInstanceOf(IllegalStateException.class);
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_REJECT_FAILED", "WITHDRAWAL",
				withdrawalId, "status=LOOKUP_FAILED");
	}

	@Test
	void preparedAttemptIsPendingAndNotCountedAsSubmitted() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		WithdrawalRequest withdrawal = payoutWithdrawal(withdrawalId);
		WithdrawalPayoutAttempt attempt = preparedAttempt(withdrawalId);
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(payoutCoordinator.prepareAndBroadcast(withdrawalId, "eth-key", "operator")).thenReturn(attempt);

		BatchWithdrawalResponse result = service.batchApprove(List.of(withdrawalId), null, "eth-key", null,
				"123456", adminId);

		assertThat(result.succeeded()).isZero();
		assertThat(result.failed()).isZero();
		assertThat(result.pending()).isEqualTo(1);
		assertThat(result.items().get(0).payoutStatus()).isEqualTo("PREPARED");
		verify(auditService).recordIndependent("operator", "WITHDRAWAL_PAYOUT_PENDING", "WITHDRAWAL",
				withdrawalId, "network=ERC20,amount=12.5,status=PREPARED,txHash=0xpayout");
	}

	@Test
	void payoutAuditFailureDoesNotChangeSingleOutcome() {
		UUID adminId = configuredAdmin();
		UUID withdrawalId = UUID.randomUUID();
		WithdrawalRequest withdrawal = payoutWithdrawal(withdrawalId);
		WithdrawalPayoutAttempt attempt = preparedAttempt(withdrawalId);
		attempt.markBroadcasted();
		when(withdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(payoutCoordinator.prepareAndBroadcast(withdrawalId, "eth-key", "operator")).thenReturn(attempt);
		when(userRepository.findById(any())).thenReturn(Optional.empty());
		doThrow(new IllegalStateException("audit store down")).when(auditService)
				.recordIndependent(any(), any(), any(), any(), any());

		WithdrawalResponse response = service.approve(withdrawalId, null, "eth-key", null, "123456", adminId,
				"operator");

		assertThat(response.payoutStatus()).isEqualTo("BROADCASTED");
	}

	@Test
	void batchContinuesWhenOnePayoutAuditFails() {
		UUID adminId = configuredAdmin();
		UUID firstId = UUID.randomUUID();
		UUID secondId = UUID.randomUUID();
		WithdrawalRequest first = payoutWithdrawal(firstId);
		WithdrawalRequest second = payoutWithdrawal(secondId);
		WithdrawalPayoutAttempt firstAttempt = preparedAttempt(firstId);
		firstAttempt.markBroadcasted();
		WithdrawalPayoutAttempt secondAttempt = preparedAttempt(secondId);
		secondAttempt.markBroadcasted();
		when(withdrawalRepository.findById(firstId)).thenReturn(Optional.of(first));
		when(withdrawalRepository.findById(secondId)).thenReturn(Optional.of(second));
		when(payoutCoordinator.prepareAndBroadcast(firstId, "eth-key", "operator")).thenReturn(firstAttempt);
		when(payoutCoordinator.prepareAndBroadcast(secondId, "eth-key", "operator")).thenReturn(secondAttempt);
		doThrow(new IllegalStateException("audit store down")).when(auditService)
				.recordIndependent(any(), any(), any(), any(), any());

		BatchWithdrawalResponse result = service.batchApprove(List.of(firstId, secondId), null, "eth-key", null,
				"123456", adminId);

		assertThat(result.succeeded()).isEqualTo(2);
		assertThat(result.failed()).isZero();
		assertThat(result.pending()).isZero();
		assertThat(result.items()).hasSize(2);
	}

	@Test
	void batchRejectsMoreThanTenIds() {
		List<UUID> ids = java.util.stream.IntStream.range(0, 11).mapToObj(index -> UUID.randomUUID()).toList();

		assertThatThrownBy(() -> service.batchPreview(ids))
				.isInstanceOf(com.reelshort.backend.admin.AdminException.class)
				.hasMessage("maximum 10 withdrawal ids allowed");
	}

	@Test
	void privateKeysMustBeOptionalBlankOrExactly64HexCharacters() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

		assertThat(validator.validate(new WithdrawalApprovalRequest("not-hex", null, null, "123456")))
				.isNotEmpty();
		assertThat(validator.validate(new WithdrawalApprovalRequest("a".repeat(65), null, null, "123456")))
				.isNotEmpty();
		assertThat(validator.validate(new WithdrawalApprovalRequest("0x" + "a".repeat(64), null, null, "123456")))
				.isEmpty();
		assertThat(validator.validate(new WithdrawalApprovalRequest("", null, null, "123456")))
				.isEmpty();
		// BEP20 私钥遵循同样的校验规则（可选、空或 64 hex）。
		assertThat(validator.validate(new WithdrawalApprovalRequest(null, null, "not-hex", "123456")))
				.isNotEmpty();
		assertThat(validator.validate(new WithdrawalApprovalRequest(null, null, "0x" + "a".repeat(64), "123456")))
				.isEmpty();
	}

	private UUID configuredAdmin() {
		UUID adminId = UUID.randomUUID();
		AdminUser admin = AdminUser.create("operator", "hash", AdminUserStatus.ACTIVE);
		admin.enableTotp("secret");
		when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(totpService.verify("secret", "123456")).thenReturn(true);
		return adminId;
	}

	private WithdrawalRequest payoutWithdrawal(UUID withdrawalId) {
		WithdrawalRequest withdrawal = mock(WithdrawalRequest.class);
		when(withdrawal.id()).thenReturn(withdrawalId);
		when(withdrawal.userId()).thenReturn(UUID.randomUUID());
		when(withdrawal.network()).thenReturn("ERC20");
		when(withdrawal.usdtAmount()).thenReturn(new BigDecimal("12.5"));
		when(withdrawal.usdtPerPoint()).thenReturn(new BigDecimal("0.01"));
		when(withdrawal.status()).thenReturn(WithdrawalStatus.PENDING);
		return withdrawal;
	}

	private WithdrawalPayoutAttempt preparedAttempt(UUID withdrawalId) {
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction(
				"ERC20", "0x2222222222222222222222222222222222222222",
				"0xdAC17F958D2ee523a2206206994597C13D831ec7", 1L, BigInteger.ONE,
				"0xsigned", "0xpayout");
		return WithdrawalPayoutAttempt.prepared(withdrawalId, 1,
				"0x1111111111111111111111111111111111111111", new BigDecimal("12.5"), signed, "operator");
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
