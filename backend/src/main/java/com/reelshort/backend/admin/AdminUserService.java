package com.reelshort.backend.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.PointAccountResponse;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.PointTransactionRepository;
import com.reelshort.backend.points.PointTransactionResponse;
import com.reelshort.backend.points.PointsService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;
import com.reelshort.backend.wallet.UserWallet;
import com.reelshort.backend.wallet.UserWalletRepository;
import com.reelshort.backend.watch.WatchRecordRepository;
import com.reelshort.backend.watch.WatchRecordResponse;
import com.reelshort.backend.withdrawal.WithdrawalRequestRepository;
import com.reelshort.backend.withdrawal.WithdrawalResponse;

@Service
public class AdminUserService {

	private final UserAccountRepository userAccountRepository;
	private final PointAccountRepository pointAccountRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final PointsService pointsService;
	private final WatchRecordRepository watchRecordRepository;
	private final UserWalletRepository userWalletRepository;
	private final WithdrawalRequestRepository withdrawalRequestRepository;
	private final AdminAuditService adminAuditService;

	public AdminUserService(UserAccountRepository userAccountRepository, PointAccountRepository pointAccountRepository,
			PointTransactionRepository pointTransactionRepository, PointsService pointsService,
			WatchRecordRepository watchRecordRepository, UserWalletRepository userWalletRepository,
			WithdrawalRequestRepository withdrawalRequestRepository, AdminAuditService adminAuditService) {
		this.userAccountRepository = userAccountRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.pointsService = pointsService;
		this.watchRecordRepository = watchRecordRepository;
		this.userWalletRepository = userWalletRepository;
		this.withdrawalRequestRepository = withdrawalRequestRepository;
		this.adminAuditService = adminAuditService;
	}

	@Transactional(readOnly = true)
	public List<AdminUserSummaryResponse> users() {
		return userAccountRepository.findAll().stream()
				.sorted(Comparator.comparing(UserAccount::createdAt).reversed())
				.map(user -> {
					AccountSnapshot account = accountSnapshot(user.id());
					return new AdminUserSummaryResponse(user.id(), user.username(), user.status(),
							account.balance(), account.frozenPoints(), account.availablePoints(),
							user.createdAt().toString());
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public AdminUserDetailResponse detail(UUID userId) {
		UserAccount user = user(userId);
		return detailResponse(user);
	}

	@Transactional
	public AdminUserDetailResponse changeStatus(String adminUsername, UUID userId, UserStatus status) {
		UserAccount user = user(userId);
		user.changeStatus(status);
		UserAccount savedUser = userAccountRepository.save(user);
		adminAuditService.record(adminUsername, "USER_STATUS_CHANGED", "USER", userId,
				"Changed user status to " + status);
		return detailResponse(savedUser);
	}

	@Transactional
	public AdminUserDetailResponse adjustPoints(String adminUsername, UUID userId, int amount, String reason,
			String idempotencyKey) {
		if (amount == 0) {
			throw new AdminException(400, "bad request");
		}
		UserAccount user = userForUpdate(userId);
		String normalizedReason = reason.trim();
		String scopedKey = scopedIdempotencyKey(adminUsername, userId, idempotencyKey);
		var existing = pointTransactionRepository.findByIdempotencyKey(scopedKey);
		if (existing.isPresent()) {
			var original = existing.get();
			if (original.amount() != amount || !Objects.equals(original.reason(), normalizedReason)) {
				throw new AdminException(409, "idempotency key reused with different request");
			}
			int frozenPoints = original.frozenPointsAfter() == null
					? accountSnapshot(userId).frozenPoints()
					: original.frozenPointsAfter();
			return detailResponse(user, original.balanceAfter(), frozenPoints,
					original.balanceAfter() - frozenPoints);
		}
		PointAccountResponse account = pointsService.adjustByAdmin(userId, amount, normalizedReason, scopedKey);
		adminAuditService.record(adminUsername, "POINTS_ADJUSTED", "USER", userId,
				"Adjusted points by " + amount + ": " + normalizedReason);
		return detailResponse(user, account.balance(), account.frozenPoints(), account.availablePoints());
	}

	@Transactional(readOnly = true)
	public List<WatchRecordResponse> watchRecords(UUID userId) {
		user(userId);
		return watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(WatchRecordResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<PointTransactionResponse> pointRecords(UUID userId) {
		user(userId);
		return pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(PointTransactionResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<WithdrawalResponse> withdrawals(UUID userId) {
		user(userId);
		return withdrawalRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(WithdrawalResponse::from)
				.toList();
	}

	private AdminUserDetailResponse detailResponse(UserAccount user) {
		AccountSnapshot account = accountSnapshot(user.id());
		return detailResponse(user, account.balance(), account.frozenPoints(), account.availablePoints());
	}

	private AdminUserDetailResponse detailResponse(UserAccount user, int balance, int frozenPoints,
			int availablePoints) {
		UserWallet wallet = userWalletRepository.findByUserId(user.id()).orElse(null);
		return new AdminUserDetailResponse(user.id(), user.username(), user.status(), balance, frozenPoints,
				availablePoints, wallet == null ? null : wallet.network(),
				wallet == null ? null : wallet.walletAddress(),
				wallet == null ? null : wallet.updatedAt().toString(),
				watchRecordRepository.countByUserId(user.id()), pointTransactionRepository.countByUserId(user.id()),
				withdrawalRequestRepository.countByUserId(user.id()), user.createdAt().toString());
	}

	private UserAccount user(UUID userId) {
		return userAccountRepository.findById(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
	}

	private AccountSnapshot accountSnapshot(UUID userId) {
		return pointAccountRepository.findByUserId(userId)
				.map(account -> new AccountSnapshot(account.balance(), account.frozenPoints(),
						account.availablePoints()))
				.orElse(new AccountSnapshot(0, 0, 0));
	}

	private UserAccount userForUpdate(UUID userId) {
		return userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
	}

	private String scopedIdempotencyKey(String adminUsername, UUID userId, String requestKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest((adminUsername + "\n" + userId + "\n" + requestKey.trim())
					.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder("admin-adjust:");
			for (byte value : bytes) {
				result.append(String.format("%02x", value));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 not available", exception);
		}
	}

	private record AccountSnapshot(int balance, int frozenPoints, int availablePoints) {
	}
}
