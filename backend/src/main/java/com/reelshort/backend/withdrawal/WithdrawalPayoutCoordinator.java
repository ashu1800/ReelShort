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
		String signingOwner = UUID.randomUUID().toString();
		Optional<WithdrawalPayoutAttempt> active = transactionService.findActive(withdrawalId);
		if (active.isPresent()) {
			return resume(active.get(), privateKey, signingOwner);
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
		requirePrivateKey(privateKey);
		WithdrawalPayoutAttempt attempt;
		if ("TRC20".equals(withdrawal.network())) {
			String hotWalletAddress = tronClient.addressFromPrivateKey(privateKey);
			attempt = transactionService.reserveTron(
					withdrawalId, signingOwner, hotWalletAddress, createdBy);
		}
		else if ("ERC20".equals(withdrawal.network())) {
			String hotWalletAddress = ethereumClient.addressFromPrivateKey(privateKey);
			BigInteger observedNonce = ethereumClient.queryPendingNonce(hotWalletAddress);
			BigInteger gasPrice = ethereumClient.queryGasPrice();
			attempt = transactionService.reserveEthereum(withdrawalId, signingOwner, hotWalletAddress,
					observedNonce, gasPrice, createdBy);
		}
		else {
			throw new WithdrawalException(400, "unsupported withdrawal network");
		}
		return finishOwnedSigningAndBroadcast(attempt, privateKey, signingOwner);
	}

	public WithdrawalPayoutAttempt retryBroadcast(UUID attemptId) {
		WithdrawalPayoutAttempt attempt = transactionService.findAttempt(attemptId)
				.orElseThrow(() -> new WithdrawalException(404, "payout attempt not found"));
		return replay(attempt);
	}

	private WithdrawalPayoutAttempt resume(WithdrawalPayoutAttempt attempt, String privateKey, String signingOwner) {
		if (attempt.status() != WithdrawalPayoutStatus.SIGNING) {
			return replay(attempt);
		}
		requirePrivateKey(privateKey);
		WithdrawalPayoutAttempt claimed = transactionService.claimSigning(attempt.id(), signingOwner);
		return finishOwnedSigningAndBroadcast(claimed, privateKey, signingOwner);
	}

	private WithdrawalPayoutAttempt finishOwnedSigningAndBroadcast(WithdrawalPayoutAttempt attempt,
			String privateKey, String signingOwner) {
		if (attempt.status() != WithdrawalPayoutStatus.SIGNING) {
			return replay(attempt);
		}
		if (!attempt.isSigningOwner(signingOwner)) {
			return attempt;
		}
		PreparedPayoutTransaction signed;
		if ("TRC20".equals(attempt.network())) {
			String derivedAddress = tronClient.addressFromPrivateKey(privateKey);
			requireMatchingWallet(attempt, derivedAddress);
			signed = tronClient.prepareTransfer(privateKey, attempt.destinationAddress(), attempt.tokenAmount());
		}
		else {
			String derivedAddress = ethereumClient.addressFromPrivateKey(privateKey);
			requireMatchingWallet(attempt, derivedAddress);
			signed = ethereumClient.signTransfer(privateKey, attempt.destinationAddress(), attempt.tokenAmount(),
					attempt.nonce(), attempt.gasPrice());
		}
		WithdrawalPayoutAttempt prepared = transactionService.completeSigning(attempt.id(), signingOwner, signed);
		return replay(prepared);
	}

	private WithdrawalPayoutAttempt replay(WithdrawalPayoutAttempt attempt) {
		if (attempt.status() == WithdrawalPayoutStatus.SIGNING) {
			return attempt;
		}
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
		if (result.disposition() == PayoutBroadcastDisposition.EXPIRED && "TRC20".equals(attempt.network())) {
			return reconcileExpiredTron(attempt, result.detail());
		}
		return transactionService.recordBroadcastResult(attempt.id(), result);
	}

	private WithdrawalPayoutAttempt reconcileExpiredTron(WithdrawalPayoutAttempt attempt, String detail) {
		PayoutChainStatus chainStatus = tronClient.queryTransactionStatus(attempt.txHash());
		return switch (chainStatus.state()) {
			case CONFIRMED, PENDING -> {
				WithdrawalPayoutAttempt broadcasted = transactionService.recordBroadcastResult(
						attempt.id(), PayoutBroadcastResult.accepted());
				yield transactionService.recordChainObservation(broadcasted.id(), chainStatus);
			}
			case FAILED -> transactionService.markRetryable(
					attempt.id(), "TRON_CHAIN_FAILED", chainStatus.detail());
			case NOT_FOUND -> transactionService.markRetryable(
					attempt.id(), "TRON_EXPIRED_NOT_FOUND", detail);
			case UNKNOWN -> transactionService.markManualReview(
					attempt.id(), "TRON transaction expired and chain state is unknown: " + chainStatus.detail());
		};
	}

	private void requireMatchingWallet(WithdrawalPayoutAttempt attempt, String derivedAddress) {
		if (!attempt.hotWalletAddress().equalsIgnoreCase(derivedAddress)) {
			throw new WithdrawalException(400, "private key does not match payout signing intent");
		}
	}

	private void requirePrivateKey(String privateKey) {
		if (privateKey == null || privateKey.isBlank()) {
			throw new WithdrawalException(400, "hot wallet private key is required");
		}
	}
}
