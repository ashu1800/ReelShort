package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.admin.AdminAuditService;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.points.PointAccount;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.system.concurrency.UserActionLocks;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.system.security.TotpService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.wallet.UserWallet;
import com.reelshort.backend.wallet.UserWalletRepository;

@Service
public class WithdrawalService {

	private final WithdrawalRequestRepository withdrawalRequestRepository;
	private final UserWalletRepository userWalletRepository;
	private final PointAccountRepository pointAccountRepository;
	private final SystemConfigService systemConfigService;
	private final UserActionLocks userActionLocks;
	private final UserAccountRepository userAccountRepository;
	private final TotpService totpService;
	private final AdminUserRepository adminUserRepository;
	private final WithdrawalPayoutCoordinator payoutCoordinator;
	private final WithdrawalPayoutAttemptRepository payoutAttemptRepository;
	private final EthereumProperties ethereumProperties;
	private final TronProperties tronProperties;
	private final AdminAuditService adminAuditService;

	public WithdrawalService(WithdrawalRequestRepository withdrawalRequestRepository,
			UserWalletRepository userWalletRepository, PointAccountRepository pointAccountRepository,
			SystemConfigService systemConfigService,
			UserActionLocks userActionLocks, UserAccountRepository userAccountRepository,
			TotpService totpService,
			AdminUserRepository adminUserRepository, WithdrawalPayoutCoordinator payoutCoordinator,
			WithdrawalPayoutAttemptRepository payoutAttemptRepository,
			EthereumProperties ethereumProperties, TronProperties tronProperties,
			AdminAuditService adminAuditService) {
		this.withdrawalRequestRepository = withdrawalRequestRepository;
		this.userWalletRepository = userWalletRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.systemConfigService = systemConfigService;
		this.userActionLocks = userActionLocks;
		this.userAccountRepository = userAccountRepository;
		this.totpService = totpService;
		this.adminUserRepository = adminUserRepository;
		this.payoutCoordinator = payoutCoordinator;
		this.payoutAttemptRepository = payoutAttemptRepository;
		this.ethereumProperties = ethereumProperties;
		this.tronProperties = tronProperties;
		this.adminAuditService = adminAuditService;
	}

	@Transactional(readOnly = true)
	public WithdrawalSummaryResponse summary(UUID userId) {
		PointAccount account = pointAccountRepository.findByUserId(userId).orElse(null);
		UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
		WithdrawalConversion conversion = conversion();
		int balance = account == null ? 0 : account.balance();
		int frozenPoints = account == null ? 0 : account.frozenPoints();
		int availablePoints = account == null ? 0 : account.availablePoints();
		int feePercent = systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_FEE_PERCENT);
		return new WithdrawalSummaryResponse(balance, frozenPoints, availablePoints,
				conversion.minimumPoints(), decimal(conversion.usdtPerPoint()), decimal(conversion.cnyPerPoint()),
				decimal(conversion.cnyPerUsd()), decimal(conversion.minimumUsd()),
				wallet == null ? null : wallet.walletAddress(), feePercent);
	}

	/**
	 * Current withdrawal conversion thresholds without any user-specific data, for operations tools.
	 */
	@Transactional(readOnly = true)
	public WithdrawalConversion.Snapshot thresholds() {
		int feePercent = systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_FEE_PERCENT);
		return conversion().toSnapshot(feePercent);
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
			WithdrawalConversion conversion = conversion();
			// 需求2: pointAmount 是用户输入的扣除总额。手续费从中扣除，不再额外扣。
			// 例如输入 1000、手续费 10%，则扣减 1000，实际提取 900。
			int minimum = conversion.minimumPoints();
			if (pointAmount < minimum) {
				throw new AdminException(400, "withdrawal amount below minimum");
			}
			int feePercent = systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_FEE_PERCENT);
			// 手续费向上取整（long 防溢出）
			long rawFee = (long) pointAmount * feePercent;
			int feeAmount = (int) ((rawFee + 99) / 100);
			// M1: 防止 feePercent 配置过高导致 withdrawable <= 0（用户全额被扣手续费）
			int withdrawable = pointAmount - feeAmount;
			if (withdrawable <= 0) {
				throw new AdminException(400, "fee percentage too high, no withdrawable points remain");
			}
			UserWallet wallet = userWalletRepository.findByUserId(userId)
					.orElseThrow(() -> new AdminException(400, "wallet required"));
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

	private WithdrawalConversion conversion() {
		try {
			return new WithdrawalConversion(
					systemConfigService.decimalValue(SystemConfigRegistry.WITHDRAW_CNY_PER_POINT),
					systemConfigService.decimalValue(SystemConfigRegistry.WITHDRAW_CNY_PER_USD),
					systemConfigService.decimalValue(SystemConfigRegistry.WITHDRAW_MINIMUM_USD));
		}
		catch (IllegalArgumentException exception) {
			throw new AdminException(400, exception.getMessage());
		}
	}

	private String decimal(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

	/**
	 * C1 fix: 单笔审批不再接受手动 txHash（安全绕过风险），统一走 approveWithTransfer 两阶段打款。
	 * 与 batchApprove 共用同一条链上转账路径，确保积分扣减和链上广播的原子性。
	 */
	public WithdrawalResponse approve(UUID withdrawalId, String tronPrivateKey, String ethPrivateKey,
			String totpCode, UUID adminUserId, String adminUsername) {
		AdminUser admin = adminUserRepository.findById(adminUserId)
				.orElseThrow(() -> new AdminException(404, "admin not found"));
		if (!admin.totpEnabled() || !totpService.verify(admin.totpSecret(), totpCode)) {
			adminAuditService.recordIndependent(adminUsername, "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
					withdrawalId, "status=TOTP_REJECTED");
			throw new AdminException(403, "2FA verification failed");
		}
		WithdrawalRequest request = withdrawal(withdrawalId);
		String privateKey = "TRC20".equals(request.network()) ? tronPrivateKey : ethPrivateKey;
		WithdrawalPayoutAttempt attempt;
		try {
			attempt = payoutCoordinator.prepareAndBroadcast(withdrawalId, privateKey,
					adminUsername);
		}
		catch (RuntimeException exception) {
			adminAuditService.recordIndependent(adminUsername, "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
					withdrawalId, payoutAuditSummary(request, "FAILED", null));
			throw exception;
		}
		adminAuditService.recordIndependent(adminUsername, "WITHDRAWAL_PAYOUT_EXECUTED", "WITHDRAWAL",
				withdrawalId, payoutAuditSummary(request, attempt.status().name(), attempt.txHash()));
		return adminResponse(withdrawalRequestRepository.findById(withdrawalId).orElseThrow());
	}

	@Transactional
	public WithdrawalResponse reject(UUID withdrawalId, String reason, String adminUsername) {
		WithdrawalRequest request = withdrawalForUpdate(withdrawalId);
		try {
			WithdrawalResponse response = userActionLocks.withUserLock(request.userId(), () -> {
				if (request.status() != WithdrawalStatus.PENDING) {
					throw new AdminException(400, "withdrawal is not pending");
				}
				if (payoutAttemptRepository.findByWithdrawalRequestIdAndActiveSlot(withdrawalId, "ACTIVE").isPresent()) {
					throw new AdminException(409, "withdrawal payout is active");
				}
				PointAccount account = accountEntityForUpdate(request.userId());
				try {
					account.releaseFrozen(request.totalDeductedPoints());
				}
				catch (IllegalStateException exception) {
					throw new AdminException(400, exception.getMessage());
				}
				pointAccountRepository.save(account);
				try {
					request.reject(reason.trim(), adminUsername);
				}
				catch (IllegalStateException exception) {
					throw new AdminException(400, exception.getMessage());
				}
				return adminResponse(withdrawalRequestRepository.save(request));
			});
			adminAuditService.record(adminUsername, "WITHDRAWAL_REJECTED", "WITHDRAWAL", withdrawalId,
					payoutAuditSummary(request, WithdrawalStatus.REJECTED.name(), request.txHash()));
			return response;
		}
		catch (RuntimeException exception) {
			adminAuditService.recordIndependent(adminUsername, "WITHDRAWAL_REJECT_FAILED", "WITHDRAWAL",
					withdrawalId, payoutAuditSummary(request, "FAILED", request.txHash()));
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
		List<WithdrawalRequest> requests = withdrawalRequestRepository.findAllById(withdrawalIds);
		Map<UUID, WithdrawalRequest> requestsById = new HashMap<>();
		for (WithdrawalRequest request : requests) {
			requestsById.put(request.id(), request);
		}
		BigDecimal total = BigDecimal.ZERO;
		List<BatchWithdrawalPreviewResponse.PreviewItem> items = new ArrayList<>();
		for (UUID withdrawalId : withdrawalIds) {
			WithdrawalRequest req = requestsById.get(withdrawalId);
			if (req == null) {
				throw new AdminException(404, "withdrawal not found");
			}
			if (req.status() == WithdrawalStatus.PENDING) {
				total = total.add(req.usdtAmount());
			}
			items.add(new BatchWithdrawalPreviewResponse.PreviewItem(
					req.id().toString(), userAccount(req.userId()), decimal(req.usdtAmount()),
					req.network(), req.walletAddress(), req.status()));
		}
		return new BatchWithdrawalPreviewResponse(
				tronAddress, ethAddress,
				decimal(total), items.size(), items);
	}

	/**
	 * Execute a batch payout: verify 2FA, then transfer USDT sequentially for each withdrawal.
	 * H2 fix: 改为尽力而为模式——某条失败后继续处理剩余提现，而非立即中断放弃所有后续。
	 * Private keys are used in-memory only, dispatched by withdrawal network.
	 */
	public BatchWithdrawalResponse batchApprove(List<UUID> withdrawalIds, String tronPrivateKey,
			String ethPrivateKey, String totpCode, UUID adminUserId) {
		validateBatchIds(withdrawalIds);
		AdminUser admin = adminUserRepository.findById(adminUserId)
				.orElseThrow(() -> new AdminException(404, "admin not found"));
		if (!admin.totpEnabled() || !totpService.verify(admin.totpSecret(), totpCode)) {
			for (UUID withdrawalId : withdrawalIds) {
				adminAuditService.recordIndependent(admin.username(), "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
						withdrawalId, "status=TOTP_REJECTED");
			}
			throw new AdminException(403, "2FA verification failed");
		}
		List<BatchWithdrawalResponse.ItemResult> results = new ArrayList<>();
		int succeeded = 0;
		int firstFailureIndex = -1;
		String firstFailureMessage = null;
		int index = 0;
		for (UUID withdrawalId : withdrawalIds) {
			WithdrawalRequest request = null;
			WithdrawalPayoutAttempt attempt;
			try {
				request = withdrawal(withdrawalId);
				String privateKey = "TRC20".equals(request.network()) ? tronPrivateKey : ethPrivateKey;
				attempt = payoutCoordinator.prepareAndBroadcast(
						withdrawalId, privateKey, admin.username());
			}
			catch (Exception exception) {
				String errorMessage = payoutErrorMessage(exception);
				results.add(new BatchWithdrawalResponse.ItemResult(
						withdrawalId.toString(), "FAILED", null, 0, errorMessage, false,
						errorMessage));
				String auditSummary = request == null
						? "status=FAILED"
						: payoutAuditSummary(request, "FAILED", null);
				adminAuditService.recordIndependent(admin.username(), "WITHDRAWAL_PAYOUT_FAILED", "WITHDRAWAL",
						withdrawalId, auditSummary);
				if (firstFailureIndex < 0) {
					firstFailureIndex = index;
					firstFailureMessage = errorMessage;
				}
				index++;
				continue;
			}
			adminAuditService.recordIndependent(admin.username(), "WITHDRAWAL_PAYOUT_EXECUTED", "WITHDRAWAL",
					withdrawalId, payoutAuditSummary(request, attempt.status().name(), attempt.txHash()));
			results.add(new BatchWithdrawalResponse.ItemResult(
					withdrawalId.toString(), attempt.status().name(), attempt.txHash(),
					attempt.confirmationCount(), attempt.failureReason(),
					attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW, null));
			succeeded++;
			index++;
		}
		return new BatchWithdrawalResponse(succeeded, results.size() - succeeded, firstFailureIndex,
				firstFailureMessage, results);
	}

	private String payoutAuditSummary(WithdrawalRequest request, String status, String txHash) {
		String summary = "network=" + request.network() + ",amount=" + decimal(request.usdtAmount())
				+ ",status=" + status;
		return txHash == null || txHash.isBlank() ? summary : summary + ",txHash=" + txHash;
	}

	private void validateBatchIds(List<UUID> withdrawalIds) {
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
