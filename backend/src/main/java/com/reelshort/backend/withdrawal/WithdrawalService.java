package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.points.PointAccount;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.system.concurrency.UserActionLocks;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.wallet.UserWallet;
import com.reelshort.backend.wallet.UserWalletRepository;

@Service
public class WithdrawalService {

	private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

	private final WithdrawalRequestRepository withdrawalRequestRepository;
	private final UserWalletRepository userWalletRepository;
	private final PointAccountRepository pointAccountRepository;
	private final SystemConfigService systemConfigService;
	private final UserActionLocks userActionLocks;
	private final UserAccountRepository userAccountRepository;
	private final WithdrawalPayoutCoordinator payoutCoordinator;
	private final PayoutBalancePreflightService balancePreflight;
	private final WithdrawalPayoutAttemptRepository payoutAttemptRepository;
	private final EthereumProperties ethereumProperties;
	private final TronProperties tronProperties;
	private final BscProperties bscProperties;
	private final AdminAuditService adminAuditService;
	private final ReentrantLock payoutExecutionLock = new ReentrantLock();

	public WithdrawalService(WithdrawalRequestRepository withdrawalRequestRepository,
			UserWalletRepository userWalletRepository, PointAccountRepository pointAccountRepository,
			SystemConfigService systemConfigService,
			UserActionLocks userActionLocks, UserAccountRepository userAccountRepository,
			WithdrawalPayoutCoordinator payoutCoordinator,
			PayoutBalancePreflightService balancePreflight,
			WithdrawalPayoutAttemptRepository payoutAttemptRepository,
			EthereumProperties ethereumProperties, TronProperties tronProperties, BscProperties bscProperties,
			AdminAuditService adminAuditService) {
		this.withdrawalRequestRepository = withdrawalRequestRepository;
		this.userWalletRepository = userWalletRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.systemConfigService = systemConfigService;
		this.userActionLocks = userActionLocks;
		this.userAccountRepository = userAccountRepository;
		this.payoutCoordinator = payoutCoordinator;
		this.balancePreflight = balancePreflight;
		this.payoutAttemptRepository = payoutAttemptRepository;
		this.ethereumProperties = ethereumProperties;
		this.tronProperties = tronProperties;
		this.bscProperties = bscProperties;
		this.adminAuditService = adminAuditService;
	}

	@Transactional(readOnly = true)
	public WithdrawalSummaryResponse summary(UUID userId) {
		PointAccount account = pointAccountRepository.findByUserId(userId).orElse(null);
		UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
		int balance = account == null ? 0 : account.balance();
		int frozenPoints = account == null ? 0 : account.frozenPoints();
		int availablePoints = account == null ? 0 : account.availablePoints();
		int feePercent = systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_FEE_PERCENT);
		WithdrawalConversion conversion = conversion(feePercent);
		return new WithdrawalSummaryResponse(balance, frozenPoints, availablePoints,
				conversion.minimumPoints(), decimal(conversion.usdtPerPoint()), decimal(conversion.usdtPer50Points()),
				decimal(conversion.minimumUsdt()),
				wallet == null ? null : wallet.walletAddress(), feePercent);
	}

	/**
	 * Current withdrawal conversion thresholds without any user-specific data, for operations tools.
	 */
	@Transactional(readOnly = true)
	public WithdrawalConversion.Snapshot thresholds() {
		int feePercent = systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_FEE_PERCENT);
		return conversion(feePercent).toSnapshot(feePercent);
	}

	@Transactional(readOnly = true)
	public List<WithdrawalResponse> userWithdrawals(UUID userId) {
		return withdrawalRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(WithdrawalResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<WithdrawalResponse> adminWithdrawals() {
		return withdrawalRequestRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(this::adminResponse)
				.toList();
	}

	@Transactional
	public WithdrawalResponse create(UUID userId, int pointAmount) {
		return userActionLocks.withUserLock(userId, () -> {
			int feePercent = systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_FEE_PERCENT);
			WithdrawalConversion conversion = conversion(feePercent);
			// 需求2: pointAmount 是用户输入的扣除总额。手续费从中扣除，不再额外扣。
			// 例如输入 1000、手续费 10%，则扣减 1000，实际提取 900。
			int minimum = conversion.minimumPoints();
			if (pointAmount < minimum) {
				throw new AdminException(400, "withdrawal amount below minimum");
			}
			int feeAmount = conversion.feeAmount(pointAmount);
			// M1: 防止 feePercent 配置过高导致 withdrawable <= 0（用户全额被扣手续费）
			int withdrawable = conversion.withdrawablePoints(pointAmount);
			if (withdrawable <= 0) {
				throw new AdminException(400, "fee percentage too high, no withdrawable points remain");
			}
			UserWallet wallet = userWalletRepository.findByUserId(userId)
					.orElseThrow(() -> new AdminException(400, "wallet required"));
			if (!"ERC20".equals(wallet.network())) {
				throw new AdminException(400, "only ERC20 withdrawals are supported");
			}
			PointAccount account = accountEntityForUpdate(userId);
			// 扣减总额 = pointAmount（手续费含在其中）
			if (!account.canUseAvailable(pointAmount)) {
				throw new AdminException(400, "insufficient available point balance");
			}
			try {
				account.freeze(pointAmount);
			}
			catch (IllegalStateException exception) {
				throw new AdminException(400, exception.getMessage());
			}
			pointAccountRepository.save(account);
			WithdrawalRequest request = WithdrawalRequest.create(userId, pointAmount, feeAmount, conversion,
					wallet.network(), wallet.walletAddress());
			return WithdrawalResponse.from(withdrawalRequestRepository.save(request));
		});
	}

	private WithdrawalConversion conversion(int feePercent) {
		try {
			return new WithdrawalConversion(
					systemConfigService.decimalValue(SystemConfigRegistry.WITHDRAW_USDT_PER_50_POINTS),
					systemConfigService.decimalValue(SystemConfigRegistry.WITHDRAW_MINIMUM_USDT), feePercent);
		}
		catch (IllegalArgumentException exception) {
			throw new AdminException(400, exception.getMessage());
		}
	}

	private String decimal(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	/**
	 * 按提现 network 选择对应链的热钱包私钥。BEP20 提现必须传 bepPrivateKey，否则无法打款。
	 */
	private String selectPrivateKey(String network, String tronPrivateKey, String ethPrivateKey,
			String bepPrivateKey) {
		return switch (network) {
			case "TRC20" -> tronPrivateKey;
			case "ERC20" -> ethPrivateKey;
			case "BEP20" -> bepPrivateKey;
			default -> throw new AdminException(400, "unsupported withdrawal network: " + network);
		};
	}

	/**
	 * C1 fix: 单笔审批不再接受手动 txHash（安全绕过风险），统一走 approveWithTransfer 两阶段打款。
	 * 与 batchApprove 共用同一条链上转账路径，确保积分扣减和链上广播的原子性。
	 */
	public WithdrawalResponse approve(UUID withdrawalId, String tronPrivateKey, String ethPrivateKey,
			String bepPrivateKey, String adminUsername) {
		return withPayoutExecutionLock(() -> approveLocked(withdrawalId, tronPrivateKey, ethPrivateKey,
				bepPrivateKey, adminUsername));
	}

	private WithdrawalResponse approveLocked(UUID withdrawalId, String tronPrivateKey, String ethPrivateKey,
			String bepPrivateKey, String adminUsername) {
		WithdrawalRequest request = null;
		WithdrawalPayoutAttempt attempt;
		try {
			request = withdrawal(withdrawalId);
			if (requiresBalancePreflight(request)) {
				balancePreflight.requireSufficient(
						List.of(request), tronPrivateKey, ethPrivateKey, bepPrivateKey);
			}
			String privateKey = selectPrivateKey(request.network(), tronPrivateKey, ethPrivateKey, bepPrivateKey);
			attempt = payoutCoordinator.prepareAndBroadcast(withdrawalId, privateKey,
					adminUsername);
		}
		catch (RuntimeException exception) {
			String summary = request == null
					? targetLookupFailureSummary(exception)
					: payoutAuditSummary(request, "FAILED", null);
			recordAuditSafely(adminUsername, "WITHDRAWAL_PAYOUT_FAILED", withdrawalId, summary);
			throw exception;
		}
		String action = isSubmitted(attempt.status())
				? "WITHDRAWAL_PAYOUT_EXECUTED"
				: isPending(attempt.status()) ? "WITHDRAWAL_PAYOUT_PENDING" : "WITHDRAWAL_PAYOUT_FAILED";
		recordAuditSafely(adminUsername, action, withdrawalId,
				payoutAuditSummary(request, attempt.status().name(), attempt.txHash()));
		return adminResponse(request, attempt);
	}

	@Transactional
	public WithdrawalResponse manualConfirm(UUID withdrawalId, String adminUsername) {
		return withPayoutExecutionLock(() -> manualConfirmLocked(withdrawalId, adminUsername));
	}

	private WithdrawalResponse manualConfirmLocked(UUID withdrawalId, String adminUsername) {
		WithdrawalRequest request = withdrawalForUpdate(withdrawalId);
		if (!"ERC20".equals(request.network())) {
			throw new AdminException(409, "only ERC20 withdrawals can be manually confirmed");
		}
		if (request.status() == WithdrawalStatus.APPROVED) {
			return adminResponse(request);
		}
		if (request.status() != WithdrawalStatus.PENDING) {
			throw new AdminException(409, "withdrawal is not pending");
		}
		WithdrawalPayoutAttempt active = payoutAttemptRepository
				.findByWithdrawalRequestIdAndActiveSlot(request.id(), "ACTIVE").orElse(null);
		if (active != null && active.status() != WithdrawalPayoutStatus.MANUAL_REVIEW) {
			throw new AdminException(409, "automatic payout attempt is still active");
		}
		WithdrawalResponse response = userActionLocks.withUserLock(request.userId(), () -> {
			PointAccount account = accountEntityForUpdate(request.userId());
			try {
				account.deductFrozen(request.totalDeductedPoints());
			}
			catch (IllegalStateException exception) {
				throw new AdminException(409, exception.getMessage());
			}
			pointAccountRepository.save(account);
			request.approve(null, "manual external ERC20 payout confirmed", adminUsername);
			return adminResponse(withdrawalRequestRepository.save(request), active);
		});
		recordAuditSafely(adminUsername, "WITHDRAWAL_MANUAL_CONFIRMED", withdrawalId,
				payoutAuditSummary(request, "MANUAL_CONFIRMED", null));
		return response;
	}

	@Transactional
	public WithdrawalResponse reject(UUID withdrawalId, String reason, String adminUsername) {
		WithdrawalRequest request = null;
		try {
			request = withdrawalForUpdate(withdrawalId);
			WithdrawalRequest lockedRequest = request;
			WithdrawalResponse response = userActionLocks.withUserLock(lockedRequest.userId(), () -> {
				if (lockedRequest.status() != WithdrawalStatus.PENDING) {
					throw new AdminException(400, "withdrawal is not pending");
				}
				if (payoutAttemptRepository.findByWithdrawalRequestIdAndActiveSlot(withdrawalId, "ACTIVE").isPresent()) {
					throw new AdminException(409, "withdrawal payout is active");
				}
				PointAccount account = accountEntityForUpdate(lockedRequest.userId());
				try {
					account.releaseFrozen(lockedRequest.totalDeductedPoints());
				}
				catch (IllegalStateException exception) {
					throw new AdminException(400, exception.getMessage());
				}
				pointAccountRepository.save(account);
				try {
					lockedRequest.reject(reason.trim(), adminUsername);
				}
				catch (IllegalStateException exception) {
					throw new AdminException(400, exception.getMessage());
				}
				return adminResponse(withdrawalRequestRepository.save(lockedRequest));
			});
			recordAuditSafely(adminUsername, "WITHDRAWAL_REJECTED", withdrawalId,
					payoutAuditSummary(lockedRequest, WithdrawalStatus.REJECTED.name(), lockedRequest.txHash()));
			return response;
		}
		catch (RuntimeException exception) {
			String summary = request == null
					? targetLookupFailureSummary(exception)
					: payoutAuditSummary(request, "FAILED", request.txHash());
			recordAuditSafely(adminUsername, "WITHDRAWAL_REJECT_FAILED", withdrawalId, summary);
			throw exception;
		}
	}

	/**
	 * Preview a batch payout without accepting or deriving any signing material.
	 */
	@Transactional(readOnly = true)
	public BatchWithdrawalPreviewResponse batchPreview(List<UUID> withdrawalIds) {
		validateBatchIds(withdrawalIds);
		String tronAddress = configuredAddress(tronProperties.getHotWalletAddress());
		String ethAddress = configuredAddress(ethereumProperties.getHotWalletAddress());
		String bepAddress = configuredAddress(bscProperties.getHotWalletAddress());
		List<WithdrawalRequest> requests = withdrawalRequestRepository.findAllById(withdrawalIds);
		Map<UUID, WithdrawalRequest> requestsById = new HashMap<>();
		for (WithdrawalRequest request : requests) {
			requestsById.put(request.id(), request);
		}
		BigDecimal total = BigDecimal.ZERO;
		List<WithdrawalRequest> selectedRequests = new ArrayList<>();
		List<BatchWithdrawalPreviewResponse.PreviewItem> items = new ArrayList<>();
		for (UUID withdrawalId : withdrawalIds) {
			WithdrawalRequest req = requestsById.get(withdrawalId);
			if (req == null) {
				throw new AdminException(404, "withdrawal not found");
			}
			requireBatchPayoutEligible(req);
			selectedRequests.add(req);
			total = total.add(req.usdtAmount());
			items.add(new BatchWithdrawalPreviewResponse.PreviewItem(
					req.id().toString(), userAccount(req.userId()), decimal(req.usdtAmount()),
					req.network(), req.walletAddress(), req.status()));
		}
		return new BatchWithdrawalPreviewResponse(
				tronAddress, ethAddress, bepAddress,
				decimal(total), items.size(), items,
				balancePreflight.estimateFees(selectedRequests).stream()
						.map(estimate -> new BatchWithdrawalPreviewResponse.FeeEstimate(
								estimate.network(), estimate.asset(), estimate.transactionCount(),
								decimal(estimate.estimatedAmount()), estimate.estimateType()))
						.toList());
	}

	/**
	 * Execute a batch payout by transferring USDT sequentially for each withdrawal.
	 * H2 fix: 改为尽力而为模式——某条失败后继续处理剩余提现，而非立即中断放弃所有后续。
	 * Private keys are used in-memory only, dispatched by withdrawal network.
	 */
	public BatchWithdrawalResponse batchApprove(List<UUID> withdrawalIds, String tronPrivateKey,
			String ethPrivateKey, String bepPrivateKey, String adminUsername) {
		validateBatchIds(withdrawalIds);
		return withPayoutExecutionLock(() -> batchApproveLocked(withdrawalIds, tronPrivateKey,
				ethPrivateKey, bepPrivateKey, adminUsername));
	}

	private BatchWithdrawalResponse batchApproveLocked(List<UUID> withdrawalIds, String tronPrivateKey,
			String ethPrivateKey, String bepPrivateKey, String adminUsername) {
		List<WithdrawalRequest> requests = new ArrayList<>();
		for (UUID withdrawalId : withdrawalIds) {
			WithdrawalRequest request = withdrawal(withdrawalId);
			requireBatchPayoutEligible(request);
			requests.add(request);
		}
		balancePreflight.requireSufficient(requests, tronPrivateKey, ethPrivateKey, bepPrivateKey);
		List<BatchWithdrawalResponse.ItemResult> results = new ArrayList<>();
		int succeeded = 0;
		int pendingCount = 0;
		int firstFailureIndex = -1;
		String firstFailureMessage = null;
		int index = 0;
		for (WithdrawalRequest request : requests) {
			UUID withdrawalId = request.id();
			WithdrawalPayoutAttempt attempt;
			try {
				String privateKey = selectPrivateKey(request.network(), tronPrivateKey, ethPrivateKey, bepPrivateKey);
				attempt = payoutCoordinator.prepareAndBroadcast(withdrawalId, privateKey, adminUsername);
			}
			catch (Exception exception) {
				String errorMessage = payoutErrorMessage(exception);
				results.add(new BatchWithdrawalResponse.ItemResult(
						withdrawalId.toString(), "FAILED", null, 0, errorMessage, false,
						null, null, errorMessage));
				String auditSummary = request == null
						? "status=FAILED" : payoutAuditSummary(request, "FAILED", null);
				recordAuditSafely(adminUsername, "WITHDRAWAL_PAYOUT_FAILED", withdrawalId, auditSummary);
				if (firstFailureIndex < 0) {
					firstFailureIndex = index;
					firstFailureMessage = errorMessage;
				}
				index++;
				continue;
			}
			boolean submitted = isSubmitted(attempt.status());
			boolean pending = isPending(attempt.status());
			String action = submitted ? "WITHDRAWAL_PAYOUT_EXECUTED"
					: pending ? "WITHDRAWAL_PAYOUT_PENDING" : "WITHDRAWAL_PAYOUT_FAILED";
			recordAuditSafely(adminUsername, action, withdrawalId,
					payoutAuditSummary(request, attempt.status().name(), attempt.txHash()));
			String failureReason = submitted || pending ? attempt.failureReason() : attemptFailureReason(attempt);
			results.add(new BatchWithdrawalResponse.ItemResult(
					withdrawalId.toString(), attempt.status().name(), attempt.txHash(),
					attempt.confirmationCount(), failureReason,
					attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW,
					attempt.status() == WithdrawalPayoutStatus.CONFIRMED
							? decimal(attempt.actualFeeAmount()) : null,
					attempt.status() == WithdrawalPayoutStatus.CONFIRMED
							? attempt.actualFeeAsset() : null,
					submitted ? null : failureReason));
			if (submitted) {
				succeeded++;
			}
			else if (pending) {
				pendingCount++;
			}
			else if (firstFailureIndex < 0) {
				firstFailureIndex = index;
				firstFailureMessage = failureReason;
			}
			index++;
		}
		return new BatchWithdrawalResponse(succeeded, results.size() - succeeded - pendingCount, pendingCount,
				firstFailureIndex, firstFailureMessage, results);
	}

	private void requireBatchPayoutEligible(WithdrawalRequest request) {
		if (request.status() != WithdrawalStatus.PENDING) {
			throw new AdminException(409, "withdrawal is not eligible for batch payout: " + request.id());
		}
		payoutAttemptRepository.findFirstByWithdrawalRequestIdOrderByAttemptNumberDesc(request.id())
				.map(WithdrawalPayoutAttempt::status)
				.filter(status -> status != WithdrawalPayoutStatus.SIGNING
						&& status != WithdrawalPayoutStatus.FAILED_RETRYABLE)
				.ifPresent(status -> {
					throw new AdminException(409, "withdrawal is not eligible for batch payout: "
							+ request.id() + " (" + status + ")");
				});
	}

	private boolean requiresBalancePreflight(WithdrawalRequest request) {
		return payoutAttemptRepository.findFirstByWithdrawalRequestIdOrderByAttemptNumberDesc(request.id())
				.map(WithdrawalPayoutAttempt::status)
				.map(status -> status == WithdrawalPayoutStatus.SIGNING
						|| status == WithdrawalPayoutStatus.FAILED_RETRYABLE)
				.orElse(true);
	}

	private <T> T withPayoutExecutionLock(Supplier<T> action) {
		if (!payoutExecutionLock.tryLock()) {
			throw new AdminException(409, "another payout execution is already in progress");
		}
		try {
			return action.get();
		}
		finally {
			payoutExecutionLock.unlock();
		}
	}

	private String payoutAuditSummary(WithdrawalRequest request, String status, String txHash) {
		String summary = "network=" + request.network() + ",amount=" + decimal(request.usdtAmount())
				+ ",status=" + status;
		return txHash == null || txHash.isBlank() ? summary : summary + ",txHash=" + txHash;
	}

	private boolean isSubmitted(WithdrawalPayoutStatus status) {
		return status == WithdrawalPayoutStatus.BROADCASTED
				|| status == WithdrawalPayoutStatus.CONFIRMED;
	}

	private boolean isPending(WithdrawalPayoutStatus status) {
		return status == WithdrawalPayoutStatus.PREPARED;
	}

	private String attemptFailureReason(WithdrawalPayoutAttempt attempt) {
		if (attempt.failureReason() != null && !attempt.failureReason().isBlank()) {
			return attempt.failureReason();
		}
		return attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW
				? "payout requires manual review"
				: "payout was not submitted";
	}

	private String targetLookupFailureSummary(RuntimeException exception) {
		if (exception instanceof AdminException adminException && adminException.statusCode() == 404) {
			return "status=NOT_FOUND";
		}
		if (exception instanceof WithdrawalException withdrawalException
				&& withdrawalException.statusCode() == 404) {
			return "status=NOT_FOUND";
		}
		return "status=LOOKUP_FAILED";
	}

	private void recordAuditSafely(String adminUsername, String action, UUID withdrawalId, String summary) {
		try {
			adminAuditService.recordIndependent(adminUsername, action, "WITHDRAWAL", withdrawalId, summary);
		}
		catch (RuntimeException exception) {
			log.warn("Payout audit unavailable; withdrawalId={} action={}", withdrawalId, action);
		}
	}

	private void validateBatchIds(List<UUID> withdrawalIds) {
		if (withdrawalIds.size() > 10) {
			throw new AdminException(400, "maximum 10 withdrawal ids allowed");
		}
		if (new HashSet<>(withdrawalIds).size() != withdrawalIds.size()) {
			throw new AdminException(400, "duplicate withdrawal ids are not allowed");
		}
	}

	private String payoutErrorMessage(Exception exception) {
		if (exception instanceof WithdrawalException || exception instanceof AdminException) {
			return exception.getMessage();
		}
		return "payout processing failed";
	}

	private WithdrawalResponse adminResponse(WithdrawalRequest request) {
		WithdrawalPayoutAttempt attempt = payoutAttemptRepository
				.findFirstByWithdrawalRequestIdOrderByAttemptNumberDesc(request.id())
				.orElse(null);
		return WithdrawalResponse.from(request, userAccount(request.userId()), attempt);
	}

	private WithdrawalResponse adminResponse(WithdrawalRequest request, WithdrawalPayoutAttempt attempt) {
		return WithdrawalResponse.from(request, userAccount(request.userId()), attempt);
	}

	private String configuredAddress(String address) {
		return address == null || address.isBlank() ? null : address.trim();
	}

	private String userAccount(UUID userId) {
		return userAccountRepository.findById(userId)
				.map(UserAccount::username)
				.orElse(null);
	}

	private WithdrawalRequest withdrawal(UUID withdrawalId) {
		return withdrawalRequestRepository.findById(withdrawalId)
				.orElseThrow(() -> new AdminException(404, "withdrawal not found"));
	}

	private PointAccount accountEntity(UUID userId) {
		return pointAccountRepository.findByUserId(userId)
				.orElseGet(() -> pointAccountRepository.saveAndFlush(PointAccount.create(userId)));
	}

	private WithdrawalRequest withdrawalForUpdate(UUID withdrawalId) {
		return withdrawalRequestRepository.findByIdForUpdate(withdrawalId)
				.orElseThrow(() -> new AdminException(404, "withdrawal not found"));
	}

	private PointAccount accountEntityForUpdate(UUID userId) {
		return pointAccountRepository.findByUserIdForUpdate(userId)
				.orElseGet(() -> pointAccountRepository.saveAndFlush(PointAccount.create(userId)));
	}
}
