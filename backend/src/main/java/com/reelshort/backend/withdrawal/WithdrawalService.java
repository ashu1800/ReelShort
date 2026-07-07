package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.points.PointAccount;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.PointTransaction;
import com.reelshort.backend.points.PointTransactionRepository;
import com.reelshort.backend.system.concurrency.UserActionLocks;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.wallet.UserWallet;
import com.reelshort.backend.wallet.UserWalletRepository;

@Service
public class WithdrawalService {

	private final WithdrawalRequestRepository withdrawalRequestRepository;
	private final UserWalletRepository userWalletRepository;
	private final PointAccountRepository pointAccountRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final SystemConfigService systemConfigService;
	private final UserActionLocks userActionLocks;
	private final UserAccountRepository userAccountRepository;

	public WithdrawalService(WithdrawalRequestRepository withdrawalRequestRepository,
			UserWalletRepository userWalletRepository, PointAccountRepository pointAccountRepository,
			PointTransactionRepository pointTransactionRepository, SystemConfigService systemConfigService,
			UserActionLocks userActionLocks, UserAccountRepository userAccountRepository) {
		this.withdrawalRequestRepository = withdrawalRequestRepository;
		this.userWalletRepository = userWalletRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.systemConfigService = systemConfigService;
		this.userActionLocks = userActionLocks;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional(readOnly = true)
	public WithdrawalSummaryResponse summary(UUID userId) {
		PointAccount account = pointAccountRepository.findByUserId(userId).orElse(null);
		UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
		BigDecimal usdtPerPoint = systemConfigService.decimalValue(SystemConfigRegistry.WITHDRAW_USDT_PER_POINT);
		int balance = account == null ? 0 : account.balance();
		int frozenPoints = account == null ? 0 : account.frozenPoints();
		int availablePoints = account == null ? 0 : account.availablePoints();
		return new WithdrawalSummaryResponse(balance, frozenPoints, availablePoints,
				systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_MINIMUM_POINTS),
				usdtPerPoint.stripTrailingZeros().toPlainString(), wallet == null ? null : wallet.walletAddress());
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
			int minimum = systemConfigService.intValue(SystemConfigRegistry.WITHDRAW_MINIMUM_POINTS);
			if (pointAmount < minimum) {
				throw new AdminException(400, "withdrawal amount below minimum");
			}
			UserWallet wallet = userWalletRepository.findByUserId(userId)
					.orElseThrow(() -> new AdminException(400, "wallet required"));
			PointAccount account = accountEntity(userId);
			if (!account.canUseAvailable(pointAmount)) {
				throw new AdminException(400, "insufficient available point balance");
			}
			account.freeze(pointAmount);
			pointAccountRepository.save(account);
			BigDecimal usdtPerPoint = systemConfigService.decimalValue(SystemConfigRegistry.WITHDRAW_USDT_PER_POINT);
			WithdrawalRequest request = WithdrawalRequest.create(userId, pointAmount, usdtPerPoint,
					wallet.network(), wallet.walletAddress());
			return WithdrawalResponse.from(withdrawalRequestRepository.save(request));
		});
	}

	@Transactional
	public WithdrawalResponse approve(UUID withdrawalId, String txHash, String note, String adminUsername) {
		WithdrawalRequest request = withdrawal(withdrawalId);
		return userActionLocks.withUserLock(request.userId(), () -> {
			if (request.status() != WithdrawalStatus.PENDING) {
				throw new AdminException(400, "withdrawal is not pending");
			}
			PointAccount account = accountEntity(request.userId());
			account.deductFrozen(request.pointAmount());
			pointAccountRepository.save(account);
			pointTransactionRepository.save(PointTransaction.withdrawal(request.userId(), request.pointAmount(),
					account.balance(), request.id().toString()));
			request.approve(txHash.trim(), note == null ? "" : note.trim(), adminUsername);
			return adminResponse(withdrawalRequestRepository.save(request));
		});
	}

	@Transactional
	public WithdrawalResponse reject(UUID withdrawalId, String reason, String adminUsername) {
		WithdrawalRequest request = withdrawal(withdrawalId);
		return userActionLocks.withUserLock(request.userId(), () -> {
			if (request.status() != WithdrawalStatus.PENDING) {
				throw new AdminException(400, "withdrawal is not pending");
			}
			PointAccount account = accountEntity(request.userId());
			account.releaseFrozen(request.pointAmount());
			pointAccountRepository.save(account);
			request.reject(reason.trim(), adminUsername);
			return adminResponse(withdrawalRequestRepository.save(request));
		});
	}

	private WithdrawalResponse adminResponse(WithdrawalRequest request) {
		return WithdrawalResponse.from(request, userAccount(request.userId()));
	}

	private String userAccount(UUID userId) {
		return userAccountRepository.findById(userId)
				.map(user -> user.phoneE164() == null ? user.username() : user.phoneE164())
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
}
