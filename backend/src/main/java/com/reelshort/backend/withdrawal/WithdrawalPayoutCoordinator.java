package com.reelshort.backend.withdrawal;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class WithdrawalPayoutCoordinator {

	private final WithdrawalPayoutTransactionService transactionService;
	private final WithdrawalRequestRepository withdrawalRepository;
	private final EthereumClient ethereumClient;
	private final TronClient tronClient;

	public WithdrawalPayoutCoordinator(WithdrawalPayoutTransactionService transactionService,
			WithdrawalRequestRepository withdrawalRepository, EthereumClient ethereumClient, TronClient tronClient) {
		this.transactionService = transactionService;
		this.withdrawalRepository = withdrawalRepository;
		this.ethereumClient = ethereumClient;
		this.tronClient = tronClient;
	}

	public WithdrawalPayoutAttempt prepareAndBroadcast(UUID withdrawalId, String privateKey, String createdBy) {
		Optional<WithdrawalPayoutAttempt> active = transactionService.findActive(withdrawalId);
		if (active.isPresent()) {
			return replay(active.get());
		}
		WithdrawalRequest withdrawal = withdrawalRepository.findById(withdrawalId)
				.orElseThrow(() -> new WithdrawalException(404, "withdrawal not found"));
		if (withdrawal.status() == WithdrawalStatus.APPROVED) {
			return transactionService.findLatestConfirmed(withdrawalId)
					.orElseThrow(() -> new WithdrawalException(409,
							"approved withdrawal has no confirmed payout attempt"));
		}
		if (withdrawal.status() != WithdrawalStatus.PENDING) {
			throw new WithdrawalException(409, "withdrawal is not pending");
		}
		if (privateKey == null || privateKey.isBlank()) {
			throw new WithdrawalException(400, "hot wallet private key is required");
		}
		WithdrawalPayoutAttempt attempt;
		if ("TRC20".equals(withdrawal.network())) {
			PreparedPayoutTransaction signed = tronClient.prepareTransfer(
					privateKey, withdrawal.walletAddress(), withdrawal.usdtAmount());
			attempt = transactionService.prepareTron(withdrawalId, signed, createdBy);
		}
		else if ("ERC20".equals(withdrawal.network())) {
			String hotWalletAddress = ethereumClient.addressFromPrivateKey(privateKey);
			BigInteger observedNonce = ethereumClient.queryPendingNonce(hotWalletAddress);
			BigInteger gasPrice = ethereumClient.queryGasPrice();
			attempt = transactionService.prepareEthereum(withdrawalId, privateKey, hotWalletAddress,
					observedNonce, gasPrice, createdBy);
		}
		else {
			throw new WithdrawalException(400, "unsupported withdrawal network");
		}
		return replay(attempt);
	}

	public WithdrawalPayoutAttempt retryBroadcast(UUID attemptId) {
		WithdrawalPayoutAttempt attempt = transactionService.findAttempt(attemptId)
				.orElseThrow(() -> new WithdrawalException(404, "payout attempt not found"));
		return replay(attempt);
	}

	private WithdrawalPayoutAttempt replay(WithdrawalPayoutAttempt attempt) {
		if (attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW) {
			throw new WithdrawalException(409, "payout attempt requires manual review");
		}
		if (attempt.status() == WithdrawalPayoutStatus.CONFIRMED
				|| attempt.status() == WithdrawalPayoutStatus.FAILED_RETRYABLE) {
			return attempt;
		}
		PayoutBroadcastResult result = "TRC20".equals(attempt.network())
				? tronClient.broadcastSignedTransaction(attempt.signedRawTransaction(), attempt.txHash())
				: ethereumClient.broadcastSignedTransaction(attempt.signedRawTransaction(), attempt.txHash());
		WithdrawalPayoutAttempt updated = transactionService.recordBroadcastResult(attempt.id(), result);
		return updated == null ? attempt : updated;
	}
}
