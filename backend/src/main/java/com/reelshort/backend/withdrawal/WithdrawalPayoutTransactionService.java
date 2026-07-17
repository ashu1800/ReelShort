package com.reelshort.backend.withdrawal;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.PointAccount;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.PointTransaction;
import com.reelshort.backend.points.PointTransactionRepository;

@Service
public class WithdrawalPayoutTransactionService {

	private final WithdrawalRequestRepository withdrawalRepository;
	private final WithdrawalPayoutAttemptRepository attemptRepository;
	private final HotWalletNonceAllocator nonceAllocator;
	private final PointAccountRepository pointAccountRepository;
	private final PointTransactionRepository pointTransactionRepository;
	private final EthereumClient ethereumClient;
	private final EthereumProperties ethereumProperties;

	public WithdrawalPayoutTransactionService(WithdrawalRequestRepository withdrawalRepository,
			WithdrawalPayoutAttemptRepository attemptRepository, HotWalletNonceAllocator nonceAllocator,
			PointAccountRepository pointAccountRepository, PointTransactionRepository pointTransactionRepository,
			EthereumClient ethereumClient, EthereumProperties ethereumProperties) {
		this.withdrawalRepository = withdrawalRepository;
		this.attemptRepository = attemptRepository;
		this.nonceAllocator = nonceAllocator;
		this.pointAccountRepository = pointAccountRepository;
		this.pointTransactionRepository = pointTransactionRepository;
		this.ethereumClient = ethereumClient;
		this.ethereumProperties = ethereumProperties;
	}

	@Transactional(readOnly = true)
	public Optional<WithdrawalPayoutAttempt> findActive(UUID withdrawalId) {
		return attemptRepository.findByWithdrawalRequestIdAndActiveSlot(withdrawalId, "ACTIVE");
	}

	@Transactional(readOnly = true)
	public Optional<WithdrawalPayoutAttempt> findLatestConfirmed(UUID withdrawalId) {
		return attemptRepository.findFirstByWithdrawalRequestIdAndStatusOrderByAttemptNumberDesc(
				withdrawalId, WithdrawalPayoutStatus.CONFIRMED);
	}

	@Transactional(readOnly = true)
	public Optional<WithdrawalPayoutAttempt> findAttempt(UUID attemptId) {
		return attemptRepository.findById(attemptId);
	}

	@Transactional
	public WithdrawalPayoutAttempt prepareEthereum(UUID withdrawalId, String privateKey,
			String hotWalletAddress, BigInteger observedChainNonce, BigInteger gasPrice, String createdBy) {
		WithdrawalRequest withdrawal = withdrawalForUpdate(withdrawalId);
		Optional<WithdrawalPayoutAttempt> active = attemptRepository.findActiveForUpdate(withdrawalId);
		if (active.isPresent()) {
			return active.get();
		}
		requirePending(withdrawal);
		BigInteger allocatedNonce = nonceAllocator.allocate(
				"ERC20", hotWalletAddress, ethereumClientChainId(), observedChainNonce);
		PreparedPayoutTransaction signed = ethereumClient.signTransfer(privateKey, withdrawal.walletAddress(),
				withdrawal.usdtAmount(), allocatedNonce, gasPrice);
		if (!signed.hotWalletAddress().equalsIgnoreCase(hotWalletAddress)) {
			throw new WithdrawalException(400, "private key does not match the derived hot wallet address");
		}
		return persistPrepared(withdrawal, signed, createdBy);
	}

	@Transactional
	public WithdrawalPayoutAttempt prepareTron(UUID withdrawalId, PreparedPayoutTransaction signed,
			String createdBy) {
		WithdrawalRequest withdrawal = withdrawalForUpdate(withdrawalId);
		Optional<WithdrawalPayoutAttempt> active = attemptRepository.findActiveForUpdate(withdrawalId);
		if (active.isPresent()) {
			return active.get();
		}
		requirePending(withdrawal);
		if (!"TRC20".equals(signed.network())) {
			throw new WithdrawalException(400, "invalid TRON payout transaction");
		}
		return persistPrepared(withdrawal, signed, createdBy);
	}

	private WithdrawalPayoutAttempt persistPrepared(WithdrawalRequest withdrawal,
			PreparedPayoutTransaction signed, String createdBy) {
		int attemptNumber = attemptRepository.maximumAttemptNumber(withdrawal.id()) + 1;
		WithdrawalPayoutAttempt attempt = WithdrawalPayoutAttempt.prepared(withdrawal.id(), attemptNumber,
				withdrawal.walletAddress(), withdrawal.usdtAmount(), signed, createdBy);
		return attemptRepository.saveAndFlush(attempt);
	}

	@Transactional
	public WithdrawalPayoutAttempt recordBroadcastResult(UUID attemptId, PayoutBroadcastResult result) {
		WithdrawalPayoutAttempt attempt = attemptForUpdate(attemptId);
		if (attempt.status() == WithdrawalPayoutStatus.CONFIRMED
				|| attempt.status() == WithdrawalPayoutStatus.FAILED_RETRYABLE
				|| attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW) {
			return attempt;
		}
		switch (result.disposition()) {
			case ACCEPTED -> attempt.markBroadcasted();
			case EXPLICITLY_REJECTED -> attempt.markFailedRetryable("BROADCAST_REJECTED", result.detail());
			case UNKNOWN -> attempt.markBroadcastUnknown(result.detail());
		}
		return attemptRepository.save(attempt);
	}

	@Transactional
	public WithdrawalPayoutAttempt recordChainObservation(UUID attemptId, PayoutChainStatus chainStatus) {
		WithdrawalPayoutAttempt attempt = attemptForUpdate(attemptId);
		if (attempt.status() == WithdrawalPayoutStatus.CONFIRMED
				|| attempt.status() == WithdrawalPayoutStatus.FAILED_RETRYABLE
				|| attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW) {
			return attempt;
		}
		attempt.recordConfirmations(chainStatus.confirmations());
		if (chainStatus.state() == PayoutChainState.FAILED) {
			attempt.markFailedRetryable("CHAIN_FAILED", chainStatus.detail());
		}
		return attemptRepository.save(attempt);
	}

	@Transactional
	public WithdrawalPayoutAttempt markManualReview(UUID attemptId, String reason) {
		WithdrawalPayoutAttempt attempt = attemptForUpdate(attemptId);
		if (attempt.status() != WithdrawalPayoutStatus.MANUAL_REVIEW) {
			attempt.markManualReview(reason);
		}
		return attemptRepository.save(attempt);
	}

	@Transactional
	public WithdrawalPayoutAttempt settleConfirmed(UUID attemptId, int confirmations) {
		WithdrawalPayoutAttempt attempt = attemptForUpdate(attemptId);
		if (attempt.status() == WithdrawalPayoutStatus.CONFIRMED) {
			return attempt;
		}
		if (attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW
				|| attempt.status() == WithdrawalPayoutStatus.FAILED_RETRYABLE) {
			throw new WithdrawalException(409, "payout attempt cannot be settled from " + attempt.status());
		}
		WithdrawalRequest withdrawal = withdrawalForUpdate(attempt.withdrawalRequestId());
		String idempotencyKey = "withdrawal:" + withdrawal.id();
		if (withdrawal.status() == WithdrawalStatus.APPROVED) {
			attempt.markConfirmed(confirmations);
			return attemptRepository.save(attempt);
		}
		requirePending(withdrawal);
		PointAccount account = pointAccountRepository.findByUserIdForUpdate(withdrawal.userId())
				.orElseThrow(() -> new WithdrawalException(409, "point account not found"));
		if (pointTransactionRepository.findByIdempotencyKey(idempotencyKey).isEmpty()) {
			try {
				account.deductFrozen(withdrawal.totalDeductedPoints());
			}
			catch (IllegalStateException exception) {
				throw new WithdrawalException(409, exception.getMessage());
			}
			pointAccountRepository.save(account);
			pointTransactionRepository.save(PointTransaction.withdrawal(withdrawal.userId(),
					withdrawal.totalDeductedPoints(), account.balance(), withdrawal.id().toString()));
		}
		withdrawal.approve(attempt.txHash(), "confirmed " + attempt.network() + " payout", attempt.createdBy());
		withdrawalRepository.save(withdrawal);
		attempt.markConfirmed(confirmations);
		return attemptRepository.save(attempt);
	}

	private long ethereumClientChainId() {
		return ethereumProperties.getChainId();
	}

	private WithdrawalRequest withdrawalForUpdate(UUID withdrawalId) {
		return withdrawalRepository.findByIdForUpdate(withdrawalId)
				.orElseThrow(() -> new WithdrawalException(404, "withdrawal not found"));
	}

	private WithdrawalPayoutAttempt attemptForUpdate(UUID attemptId) {
		return attemptRepository.findByIdForUpdate(attemptId)
				.orElseThrow(() -> new WithdrawalException(404, "payout attempt not found"));
	}

	private void requirePending(WithdrawalRequest withdrawal) {
		if (withdrawal.status() != WithdrawalStatus.PENDING) {
			throw new WithdrawalException(409, "withdrawal is not pending");
		}
	}
}
