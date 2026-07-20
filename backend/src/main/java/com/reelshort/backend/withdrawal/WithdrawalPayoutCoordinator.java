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
	private final BscClient bscClient;
	private final EthereumProperties ethereumProperties;
	private final TronProperties tronProperties;
	private final BscProperties bscProperties;

	public WithdrawalPayoutCoordinator(WithdrawalPayoutTransactionService transactionService,
			WithdrawalRequestRepository withdrawalRepository, EthereumClient ethereumClient, TronClient tronClient,
			BscClient bscClient, EthereumProperties ethereumProperties, TronProperties tronProperties,
			BscProperties bscProperties) {
		this.transactionService = transactionService;
		this.withdrawalRepository = withdrawalRepository;
		this.ethereumClient = ethereumClient;
		this.tronClient = tronClient;
		this.bscClient = bscClient;
		this.ethereumProperties = ethereumProperties;
		this.tronProperties = tronProperties;
		this.bscProperties = bscProperties;
	}

	public WithdrawalPayoutAttempt prepareAndBroadcast(UUID withdrawalId, String privateKey, String createdBy) {
		privateKey = PrivateKeyNormalizer.normalize(privateKey);
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
			requireConfiguredWallet(tronProperties.getHotWalletAddress(), hotWalletAddress, false);
			attempt = transactionService.reserveTron(
					withdrawalId, signingOwner, hotWalletAddress, createdBy);
		}
		else if ("ERC20".equals(withdrawal.network())) {
			String hotWalletAddress = ethereumClient.addressFromPrivateKey(privateKey);
			requireConfiguredWallet(ethereumProperties.getHotWalletAddress(), hotWalletAddress, true);
			BigInteger observedNonce = ethereumClient.queryPendingNonce(hotWalletAddress);
			BigInteger gasPrice = ethereumClient.queryGasPrice();
			attempt = transactionService.reserveEthereum(withdrawalId, signingOwner, hotWalletAddress,
					observedNonce, gasPrice, createdBy);
		}
		else if ("BEP20".equals(withdrawal.network())) {
			String hotWalletAddress = bscClient.addressFromPrivateKey(privateKey);
			requireConfiguredWallet(bscProperties.getHotWalletAddress(), hotWalletAddress, true);
			BigInteger observedNonce = bscClient.queryPendingNonce(hotWalletAddress);
			BigInteger gasPrice = bscClient.queryGasPrice();
			attempt = transactionService.reserveBep20(withdrawalId, signingOwner, hotWalletAddress,
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
			requireConfiguredWallet(tronProperties.getHotWalletAddress(), derivedAddress, false);
			requireIntentUsesWallet(attempt, derivedAddress, false);
			signed = tronClient.prepareTransfer(privateKey, attempt.destinationAddress(), attempt.tokenAmount());
		}
		else if ("ERC20".equals(attempt.network())) {
			String derivedAddress = ethereumClient.addressFromPrivateKey(privateKey);
			requireConfiguredWallet(ethereumProperties.getHotWalletAddress(), derivedAddress, true);
			requireIntentUsesWallet(attempt, derivedAddress, true);
			signed = ethereumClient.signTransfer(privateKey, attempt.destinationAddress(), attempt.tokenAmount(),
				attempt.nonce(), attempt.gasPrice());
		}
		else if ("BEP20".equals(attempt.network())) {
			String derivedAddress = bscClient.addressFromPrivateKey(privateKey);
			requireConfiguredWallet(bscProperties.getHotWalletAddress(), derivedAddress, true);
			requireIntentUsesWallet(attempt, derivedAddress, true);
			signed = bscClient.signTransfer(privateKey, attempt.destinationAddress(), attempt.tokenAmount(),
				attempt.nonce(), attempt.gasPrice());
		}
		else {
			throw new WithdrawalException(400, "unsupported withdrawal network");
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
		PayoutBroadcastResult result;
		if ("TRC20".equals(attempt.network())) {
			result = tronClient.broadcastSignedTransaction(attempt.signedRawTransaction(), attempt.txHash());
			if (result.disposition() == PayoutBroadcastDisposition.EXPIRED) {
				return reconcileExpiredTron(attempt, result.detail());
			}
		}
		else if ("ERC20".equals(attempt.network())) {
			result = ethereumClient.broadcastSignedTransaction(attempt.signedRawTransaction(), attempt.txHash());
		}
		else if ("BEP20".equals(attempt.network())) {
			result = bscClient.broadcastSignedTransaction(attempt.signedRawTransaction(), attempt.txHash());
		}
		else {
			throw new WithdrawalException(400, "unsupported withdrawal network");
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
			case NOT_FOUND -> transactionService.markManualReview(
					attempt.id(), "TRON transaction expired and was not found by the configured node: " + detail);
			case UNKNOWN -> transactionService.markManualReview(
					attempt.id(), "TRON transaction expired and chain state is unknown: " + chainStatus.detail());
		};
	}

	private void requireIntentUsesWallet(WithdrawalPayoutAttempt attempt, String expectedAddress, boolean ignoreCase) {
		boolean matches = ignoreCase
				? attempt.hotWalletAddress().equalsIgnoreCase(expectedAddress.trim())
				: attempt.hotWalletAddress().equals(expectedAddress.trim());
		if (!matches) {
			throw new WithdrawalException(409, "payout signing intent does not use configured hot wallet");
		}
	}

	private void requireConfiguredWallet(String configuredAddress, String derivedAddress, boolean ignoreCase) {
		if (configuredAddress == null || configuredAddress.isBlank()) {
			return;
		}
		boolean matches = ignoreCase
				? configuredAddress.trim().equalsIgnoreCase(derivedAddress)
				: configuredAddress.trim().equals(derivedAddress);
		if (!matches) {
			throw new WithdrawalException(400, "private key does not match configured hot wallet");
		}
	}

	private void requirePrivateKey(String privateKey) {
		if (privateKey == null || privateKey.isBlank()) {
			throw new WithdrawalException(400, "hot wallet private key is required");
		}
	}
}
