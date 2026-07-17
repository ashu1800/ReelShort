package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
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

	private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

	private final WithdrawalRequestRepository withdrawalRequestRepository;
	private final UserWalletRepository userWalletRepository;
	private final PointAccountRepository pointAccountRepository;
	private final SystemConfigService systemConfigService;
	private final UserActionLocks userActionLocks;
	private final UserAccountRepository userAccountRepository;
	private final EthereumClient ethereumClient;
	private final TronClient tronClient;
	private final TotpService totpService;
	private final AdminUserRepository adminUserRepository;
	private final WithdrawalPayoutCoordinator payoutCoordinator;
	private final WithdrawalPayoutAttemptRepository payoutAttemptRepository;

	public WithdrawalService(WithdrawalRequestRepository withdrawalRequestRepository,
			UserWalletRepository userWalletRepository, PointAccountRepository pointAccountRepository,
			SystemConfigService systemConfigService,
			UserActionLocks userActionLocks, UserAccountRepository userAccountRepository,
			EthereumClient ethereumClient, TronClient tronClient, TotpService totpService,
			AdminUserRepository adminUserRepository, WithdrawalPayoutCoordinator payoutCoordinator,
			WithdrawalPayoutAttemptRepository payoutAttemptRepository) {
		this.withdrawalRequestRepository = withdrawalRequestRepository;
		this.userWalletRepository = userWalletRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.systemConfigService = systemConfigService;
		this.userActionLocks = userActionLocks;
		this.userAccountRepository = userAccountRepository;
		this.ethereumClient = ethereumClient;
		this.tronClient = tronClient;
		this.totpService = totpService;
		this.adminUserRepository = adminUserRepository;
		this.payoutCoordinator = payoutCoordinator;
		this.payoutAttemptRepository = payoutAttemptRepository;
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
			throw new AdminException(403, "2FA verification failed");
		}
		WithdrawalRequest request = withdrawal(withdrawalId);
		String privateKey = "TRC20".equals(request.network()) ? tronPrivateKey : ethPrivateKey;
		payoutCoordinator.prepareAndBroadcast(withdrawalId, privateKey, adminUsername);
		return adminResponse(withdrawalRequestRepository.findById(withdrawalId).orElseThrow());
	}

	@Transactional
	public WithdrawalResponse reject(UUID withdrawalId, String reason, String adminUsername) {
		WithdrawalRequest request = withdrawalForUpdate(withdrawalId);
		return userActionLocks.withUserLock(request.userId(), () -> {
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
	}

	/**
	 * Preview a batch payout: shows total USDT, hot wallet balances, and per-item details.
	 * The hot wallet private key is provided by the admin to query balances; it is not stored.
	 */
	@Transactional(readOnly = true)
	public BatchWithdrawalPreviewResponse batchPreview(List<UUID> withdrawalIds, String tronPrivateKey,
			String ethPrivateKey) {
		// 分别查询两条链的热钱包余额（如果对应私钥提供了）
		String tronAddress = null;
		BigDecimal tronUsdt = BigDecimal.ZERO;
		BigDecimal tronTrx = BigDecimal.ZERO;
		if (tronPrivateKey != null && !tronPrivateKey.isBlank()) {
			tronAddress = tronClient.addressFromPrivateKey(tronPrivateKey);
			tronUsdt = tronClient.getUsdtBalance(tronAddress);
			tronTrx = tronClient.getTrxBalance(tronAddress);
		}
		String ethAddress = null;
		BigDecimal ethUsdt = BigDecimal.ZERO;
		BigDecimal ethEth = BigDecimal.ZERO;
		if (ethPrivateKey != null && !ethPrivateKey.isBlank()) {
			ethAddress = ethereumClient.addressFromPrivateKey(ethPrivateKey);
			ethUsdt = ethereumClient.getUsdtBalance(ethAddress);
			ethEth = ethereumClient.getEthBalance(ethAddress);
		}
		List<WithdrawalRequest> requests = withdrawalRequestRepository.findAllById(withdrawalIds);
		BigDecimal total = BigDecimal.ZERO;
		BigDecimal tronPendingTotal = BigDecimal.ZERO;
		BigDecimal ethPendingTotal = BigDecimal.ZERO;
		List<BatchWithdrawalPreviewResponse.PreviewItem> items = new ArrayList<>();
		for (WithdrawalRequest req : requests) {
			if (req.status() == WithdrawalStatus.PENDING) {
				total = total.add(req.usdtAmount());
				if ("TRC20".equals(req.network())) {
					tronPendingTotal = tronPendingTotal.add(req.usdtAmount());
				}
				else {
					ethPendingTotal = ethPendingTotal.add(req.usdtAmount());
				}
			}
			items.add(new BatchWithdrawalPreviewResponse.PreviewItem(
					req.id().toString(), userAccount(req.userId()), decimal(req.usdtAmount()),
					req.network(), req.walletAddress()));
		}
		// M3: 余额不足预警（不阻断，由管理员决定是否继续）
		if (tronAddress != null && tronUsdt.compareTo(tronPendingTotal) < 0) {
			log.warn("TRC20 hot wallet USDT balance {} insufficient for pending total {}",
					tronUsdt, tronPendingTotal);
		}
		if (ethAddress != null && ethUsdt.compareTo(ethPendingTotal) < 0) {
			log.warn("ERC20 hot wallet USDT balance {} insufficient for pending total {}",
					ethUsdt, ethPendingTotal);
		}
		return new BatchWithdrawalPreviewResponse(
				tronAddress, decimal(tronUsdt), decimal(tronTrx),
				ethAddress, decimal(ethUsdt), decimal(ethEth),
				decimal(total), items.size(), items);
	}

	/**
	 * Execute a batch payout: verify 2FA, then transfer USDT sequentially for each withdrawal.
	 * H2 fix: 改为尽力而为模式——某条失败后继续处理剩余提现，而非立即中断放弃所有后续。
	 * Private keys are used in-memory only, dispatched by withdrawal network.
	 */
	public BatchWithdrawalResponse batchApprove(List<UUID> withdrawalIds, String tronPrivateKey,
			String ethPrivateKey, String totpCode, UUID adminUserId) {
		AdminUser admin = adminUserRepository.findById(adminUserId)
				.orElseThrow(() -> new AdminException(404, "admin not found"));
		if (!admin.totpEnabled() || !totpService.verify(admin.totpSecret(), totpCode)) {
			throw new AdminException(403, "2FA verification failed");
		}
		List<BatchWithdrawalResponse.ItemResult> results = new ArrayList<>();
		int succeeded = 0;
		int firstFailureIndex = -1;
		String firstFailureMessage = null;
		int index = 0;
		for (UUID withdrawalId : withdrawalIds) {
			try {
				WithdrawalRequest request = withdrawal(withdrawalId);
				String privateKey = "TRC20".equals(request.network()) ? tronPrivateKey : ethPrivateKey;
				WithdrawalPayoutAttempt attempt = payoutCoordinator.prepareAndBroadcast(
						withdrawalId, privateKey, admin.username());
				results.add(new BatchWithdrawalResponse.ItemResult(
						withdrawalId.toString(), attempt.status().name(), attempt.txHash(), null));
				succeeded++;
			}
			catch (Exception exception) {
				results.add(new BatchWithdrawalResponse.ItemResult(
						withdrawalId.toString(), "FAILED", null, exception.getMessage()));
				if (firstFailureIndex < 0) {
					firstFailureIndex = index;
					firstFailureMessage = exception.getMessage();
				}
				// H2: 继续处理剩余提现而非中断
			}
			index++;
		}
		return new BatchWithdrawalResponse(succeeded, firstFailureIndex, firstFailureMessage, results);
	}

	private WithdrawalResponse adminResponse(WithdrawalRequest request) {
		return WithdrawalResponse.from(request, userAccount(request.userId()));
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
