package com.reelshort.backend.points;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.auth.PhoneIdentity;
import com.reelshort.backend.auth.PhoneNumberNormalizer;
import com.reelshort.backend.system.concurrency.UserActionLocks;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class PointsService {

	private static final List<Integer> REWARD_STAGES = List.of(25, 50, 75, 100);

	private final PointTransactionRepository pointTransactionRepository;
	private final PointTransferRepository pointTransferRepository;
	private final PointAccountRepository pointAccountRepository;
	private final UserAccountRepository userAccountRepository;
	private final PhoneNumberNormalizer phoneNumberNormalizer;
	private final UserActionLocks userActionLocks;
	private final PointAwardTransaction pointAwardTransaction;
	private final SystemConfigService systemConfigService;

	public PointsService(PointTransactionRepository pointTransactionRepository,
			PointTransferRepository pointTransferRepository, PointAccountRepository pointAccountRepository,
			UserAccountRepository userAccountRepository, PhoneNumberNormalizer phoneNumberNormalizer,
			UserActionLocks userActionLocks, PointAwardTransaction pointAwardTransaction,
			SystemConfigService systemConfigService) {
		this.pointTransactionRepository = pointTransactionRepository;
		this.pointTransferRepository = pointTransferRepository;
		this.pointAccountRepository = pointAccountRepository;
		this.userAccountRepository = userAccountRepository;
		this.phoneNumberNormalizer = phoneNumberNormalizer;
		this.userActionLocks = userActionLocks;
		this.pointAwardTransaction = pointAwardTransaction;
		this.systemConfigService = systemConfigService;
	}

	public PointAccountResponse account(UUID userId) {
		return userActionLocks.withUserLock(userId, () -> PointAccountResponse.from(pointAwardTransaction.accountEntity(userId)));
	}

	@Transactional(readOnly = true)
	public List<PointTransactionResponse> records(UUID userId) {
		return pointTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(PointTransactionResponse::from)
				.toList();
	}

	public WatchRewardResult awardWatchProgress(UUID userId, String bookId, int episodeNum, int progressPercent) {
		int stagePoints = systemConfigService.intValue(SystemConfigRegistry.POINTS_WATCH_STAGE_POINTS);
		return userActionLocks.withUserLock(userId, () -> pointAwardTransaction.awardWatchProgress(userId, bookId,
				episodeNum, progressPercent, REWARD_STAGES, stagePoints));
	}

	public PointAccountResponse adjustByAdmin(UUID userId, int amount, String reason) {
		return userActionLocks.withUserLock(userId,
				() -> PointAccountResponse.from(pointAwardTransaction.adjustByAdmin(userId, amount, reason)));
	}

	public PointAccountResponse creditRechargeOrder(UUID userId, String orderNo, int amount) {
		return userActionLocks.withUserLock(userId,
				() -> PointAccountResponse.from(pointAwardTransaction.creditRechargeOrder(userId, orderNo, amount)));
	}

	@Transactional
	public PointTransferResponse transfer(UUID senderUserId, String recipientAccount, int pointAmount) {
		PhoneIdentity recipientPhone = phoneNumberNormalizer.normalizeAccount(recipientAccount);
		UserAccount sender = user(senderUserId);
		UserAccount recipient = userAccountRepository.findByPhoneE164(recipientPhone.e164())
				.orElseThrow(() -> new AdminException(404, "recipient account not found"));
		if (sender.id().equals(recipient.id())) {
			throw new AdminException(400, "cannot transfer to self");
		}
		if (sender.status() != UserStatus.ACTIVE || recipient.status() != UserStatus.ACTIVE) {
			throw new AdminException(403, "user disabled");
		}
		int minimum = systemConfigService.intValue(SystemConfigRegistry.POINTS_TRANSFER_MINIMUM_POINTS);
		if (pointAmount < minimum) {
			throw new AdminException(400, "transfer amount below minimum");
		}
		return withTwoUserLocks(sender.id(), recipient.id(), () -> {
			PointAccount senderAccount = accountEntity(sender.id());
			PointAccount recipientPointAccount = accountEntity(recipient.id());
			if (!senderAccount.canUseAvailable(pointAmount)) {
				throw new AdminException(400, "insufficient available point balance");
			}
			senderAccount.deductAvailable(pointAmount);
			recipientPointAccount.add(pointAmount);
			pointAccountRepository.save(senderAccount);
			pointAccountRepository.save(recipientPointAccount);
			PointTransfer transfer = pointTransferRepository.save(PointTransfer.create(sender.id(), recipient.id(),
					sender.phoneE164(), recipient.phoneE164(), pointAmount));
			pointTransactionRepository.save(PointTransaction.transferOut(sender.id(), pointAmount,
					senderAccount.balance(), transfer.id().toString()));
			pointTransactionRepository.save(PointTransaction.transferIn(recipient.id(), pointAmount,
					recipientPointAccount.balance(), transfer.id().toString()));
			return PointTransferResponse.from(transfer, sender.id());
		});
	}

	@Transactional(readOnly = true)
	public List<PointTransferResponse> transfers(UUID userId) {
		return pointTransferRepository.findBySenderUserIdOrRecipientUserIdOrderByCreatedAtDesc(userId, userId).stream()
				.map(transfer -> PointTransferResponse.from(transfer, userId))
				.toList();
	}

	private UserAccount user(UUID userId) {
		return userAccountRepository.findById(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
	}

	private PointAccount accountEntity(UUID userId) {
		return pointAccountRepository.findByUserId(userId)
				.orElseGet(() -> pointAccountRepository.saveAndFlush(PointAccount.create(userId)));
	}

	private <T> T withTwoUserLocks(UUID firstUserId, UUID secondUserId, Supplier<T> action) {
		if (firstUserId.compareTo(secondUserId) <= 0) {
			return userActionLocks.withUserLock(firstUserId,
					() -> userActionLocks.withUserLock(secondUserId, action));
		}
		return userActionLocks.withUserLock(secondUserId,
				() -> userActionLocks.withUserLock(firstUserId, action));
	}
}
