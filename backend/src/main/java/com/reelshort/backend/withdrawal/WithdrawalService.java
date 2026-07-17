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
import com.reelshort.backend.points.PointTransaction;
import com.reelshort.backend.points.PointTransactionRepository;
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
	private final PointTransactionRepository pointTransactionRepository;
	private final SystemConfigService systemConfigService;
	private final UserActionLocks userActionLocks;
	private final UserAccountRepository userAccountRepository;
	private final EthereumClient ethereumClient;
	private final TotpService totpService;
	private final AdminUserRepository adminUserRepository;

	public WithdrawalService(WithdrawalRequestRepository withdrawalRequestRepository,
			UserWalletRepository userWalletRepository, PointAccountRepository pointAccountRepository,
			PointTransactionRepository pointTransactionRepository, SystemConfigService systemConfigService,
			UserActionLocks userActionLocks, UserAccountRepository userAccountRepository,
			EthereumClient ethereumClient, TotpService totpService, AdminUserRepository adminUserRepository) {
		this.withdrawalRequestRepository = withdrawalRequestRepository;
		this.userWalletRepository = userWalletRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.systemConfigService = systemConfigService;
		this.userActionLocks = userActionLocks;
		this.userAccountRepository = userAccountRepository;
		this.ethereumClient = ethereumClient;
		this.totpService = totpService;
		this.adminUserRepository = adminUserRepository;
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
			// balance 永远是真实整数积分，pointAmount 直接使用，无需 ×10 缩放。
			// M7: 门槛基于实际提取额 pointAmount（用户真正拿到的积分价值），而非扣减总额。
			int minimum = conversion.minimumPoints();
			if (pointAmount < minimum) {
				throw new AdminException(400, "withdrawal amount below minimum");
			}
			int feePercent = systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_FEE_PERCENT);
			// L1: 手续费向上取整，避免平台少收（long 防溢出）。
			long rawFee = (long) pointAmount * feePercent;
			int feeAmount = (int) ((rawFee + 99) / 100);
			int totalDeduct = pointAmount + feeAmount;
			UserWallet wallet = userWalletRepository.findByUserId(userId)
					.orElseThrow(() -> new AdminException(400, "wallet required"));
				PointAccount account = accountEntityForUpdate(userId);
			if (!account.canUseAvailable(totalDeduct)) {
				throw new AdminException(400, "insufficient available point balance");
			}
			try {
				account.freeze(totalDeduct);
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

	@Transactional
	public WithdrawalResponse approve(UUID withdrawalId, String txHash, String note, String totpCode,
			UUID adminUserId, String adminUsername) {
		// H1: 单笔提现也必须验证 2FA，与批量打款保持一致。
		AdminUser admin = adminUserRepository.findById(adminUserId)
				.orElseThrow(() -> new AdminException(404, "admin not found"));
		if (!admin.totpEnabled() || !totpService.verify(admin.totpSecret(), totpCode)) {
			throw new AdminException(403, "2FA verification failed");
		}
		WithdrawalRequest request = withdrawalForUpdate(withdrawalId);
		return userActionLocks.withUserLock(request.userId(), () -> {
			if (request.status() != WithdrawalStatus.PENDING) {
				throw new AdminException(400, "withdrawal is not pending");
			}
			PointAccount account = accountEntityForUpdate(request.userId());
			try {
				account.deductFrozen(request.totalDeductedPoints());
			}
			catch (IllegalStateException exception) {
				throw new AdminException(400, exception.getMessage());
			}
			pointAccountRepository.save(account);
			pointTransactionRepository.save(PointTransaction.withdrawal(request.userId(), request.totalDeductedPoints(),
					account.balance(), request.id().toString()));
			try {
				request.approve(txHash.trim(), note == null ? "" : note.trim(), adminUsername);
			}
			catch (IllegalStateException exception) {
				throw new AdminException(400, exception.getMessage());
			}
			return adminResponse(withdrawalRequestRepository.save(request));
		});
	}

	@Transactional
	public WithdrawalResponse reject(UUID withdrawalId, String reason, String adminUsername) {
		WithdrawalRequest request = withdrawalForUpdate(withdrawalId);
		return userActionLocks.withUserLock(request.userId(), () -> {
			if (request.status() != WithdrawalStatus.PENDING) {
				throw new AdminException(400, "withdrawal is not pending");
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
	public BatchWithdrawalPreviewResponse batchPreview(List<UUID> withdrawalIds, String hotWalletPrivateKey) {
		String hotWalletAddress = ethereumClient.addressFromPrivateKey(hotWalletPrivateKey);
		BigDecimal usdtBalance = ethereumClient.getUsdtBalance(hotWalletAddress);
		BigDecimal ethBalance = ethereumClient.getEthBalance(hotWalletAddress);
		List<WithdrawalRequest> requests = withdrawalRequestRepository.findAllById(withdrawalIds);
		BigDecimal total = BigDecimal.ZERO;
		List<BatchWithdrawalPreviewResponse.PreviewItem> items = new ArrayList<>();
		for (WithdrawalRequest req : requests) {
			if (req.status() == WithdrawalStatus.PENDING) {
				total = total.add(req.usdtAmount());
			}
			items.add(new BatchWithdrawalPreviewResponse.PreviewItem(
					req.id().toString(), userAccount(req.userId()), decimal(req.usdtAmount()), req.walletAddress()));
		}
		return new BatchWithdrawalPreviewResponse(hotWalletAddress, decimal(usdtBalance), decimal(ethBalance),
				decimal(total), items.size(), items);
	}

	/**
	 * Execute a batch payout: verify 2FA, then transfer USDT sequentially for each withdrawal.
	 * Stops on first failure. The hot wallet private key is used in-memory only.
	 */
	public BatchWithdrawalResponse batchApprove(List<UUID> withdrawalIds, String hotWalletPrivateKey,
			String totpCode, UUID adminUserId) {
		AdminUser admin = adminUserRepository.findById(adminUserId)
				.orElseThrow(() -> new AdminException(404, "admin not found"));
		if (!admin.totpEnabled() || !totpService.verify(admin.totpSecret(), totpCode)) {
			throw new AdminException(403, "2FA verification failed");
		}
		List<BatchWithdrawalResponse.ItemResult> results = new ArrayList<>();
		int succeeded = 0;
		int index = 0;
		for (UUID withdrawalId : withdrawalIds) {
			try {
				String txHash = approveWithTransfer(withdrawalId, hotWalletPrivateKey, admin.username());
				results.add(new BatchWithdrawalResponse.ItemResult(
						withdrawalId.toString(), "APPROVED", txHash, null));
				succeeded++;
			}
			catch (Exception exception) {
				results.add(new BatchWithdrawalResponse.ItemResult(
						withdrawalId.toString(), "FAILED", null, exception.getMessage()));
				return new BatchWithdrawalResponse(succeeded, index, exception.getMessage(), results);
			}
			index++;
		}
		return new BatchWithdrawalResponse(succeeded, -1, null, results);
	}

	/**
	 * Lock the withdrawal, deduct frozen points and mark BROADCASTING in a DB transaction, then
	 * broadcast TRC20 USDT transfer outside the transaction. If broadcast succeeds, mark APPROVED
	 * in a separate transaction. If broadcast fails, mark BROADCAST_FAILED (points already deducted,
	 * requires manual refund or retry).
	 *
	 * <p>H2 fix: previously broadcast happened INSIDE the DB transaction — if DB write failed after
	 * broadcast, points were NOT deducted but USDT was already on-chain (platform loses real money).
	 * Now points are deducted first (transactionally), broadcast happens after commit, and any
	 * broadcast failure leaves the order in BROADCAST_FAILED state for manual reconciliation.
	 */
	/**
	 * H2: 两阶段提现——先在事务内扣减积分并标记 BROADCAST_FAILED（兜底状态），事务提交后再广播。
	 * 广播成功则另起事务标记 APPROVED+txHash；广播失败则保持 BROADCAST_FAILED（积分已扣，需人工对账）。
	 * 这样避免了"广播成功但 DB 失败导致平台损失真实 USDT"的旧风险。
	 *
	 * <p>Phase 1: {@link #deductAndMarkForBroadcast} — 事务内扣减积分，标记 BROADCAST_FAILED。
	 * <br>Phase 2: 事务外广播 TRC20 转账。
	 * <br>Phase 3: {@link #markApprovedAfterBroadcast} — 广播成功后新事务标记 APPROVED。
	 */
	public String approveWithTransfer(UUID withdrawalId, String hotWalletPrivateKey, String adminUsername) {
		// Phase 1: 扣减积分 + 标记兜底状态（事务内）
		UUID userId = deductAndMarkForBroadcast(withdrawalId, adminUsername);
		// Phase 2: 读取已提交的 request（仅用于获取广播参数），广播在事务外
		WithdrawalRequest broadcasted = withdrawalRequestRepository.findById(withdrawalId).orElseThrow();
		try {
			String txHash = ethereumClient.transferUSDT(hotWalletPrivateKey, broadcasted.walletAddress(),
					broadcasted.usdtAmount());
			// Phase 3: 广播成功，标记 APPROVED（新事务）
			markApprovedAfterBroadcast(withdrawalId, txHash, adminUsername);
			return txHash;
		}
		catch (RuntimeException exception) {
			// 广播失败：积分已扣，状态已是 BROADCAST_FAILED，记录错误日志供人工对账
			log.error("ERC-20 broadcast failed for withdrawal {} (points already deducted): {}",
					withdrawalId, exception.getMessage());
			throw exception;
		}
	}

	@Transactional
	UUID deductAndMarkForBroadcast(UUID withdrawalId, String adminUsername) {
		WithdrawalRequest request = withdrawalForUpdate(withdrawalId);
		return userActionLocks.withUserLock(request.userId(), () -> {
			if (request.status() != WithdrawalStatus.PENDING) {
				throw new AdminException(400, "withdrawal is not pending");
			}
			PointAccount account = accountEntityForUpdate(request.userId());
			try {
				account.deductFrozen(request.totalDeductedPoints());
			}
			catch (IllegalStateException exception) {
				throw new AdminException(400, exception.getMessage());
			}
			pointAccountRepository.save(account);
			pointTransactionRepository.save(PointTransaction.withdrawal(request.userId(), request.totalDeductedPoints(),
					account.balance(), request.id().toString()));
			// 先标记 BROADCAST_FAILED 作为兜底：若后续广播失败/进程崩溃，状态正确反映"积分已扣但未上链"
			request.markBroadcastFailed("broadcast pending", adminUsername);
			withdrawalRequestRepository.save(request);
			return request.userId();
		});
	}

	@Transactional
	void markApprovedAfterBroadcast(UUID withdrawalId, String txHash, String adminUsername) {
		WithdrawalRequest request = withdrawalForUpdate(withdrawalId);
		if (request.status() == WithdrawalStatus.BROADCAST_FAILED) {
			request.markApprovedFromBroadcast(txHash, "auto ERC-20 transfer", adminUsername);
			withdrawalRequestRepository.save(request);
		}
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
