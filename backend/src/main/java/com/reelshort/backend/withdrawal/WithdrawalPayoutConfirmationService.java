package com.reelshort.backend.withdrawal;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WithdrawalPayoutConfirmationService {

	private static final Logger log = LoggerFactory.getLogger(WithdrawalPayoutConfirmationService.class);

	private final WithdrawalPayoutAttemptRepository attemptRepository;
	private final WithdrawalPayoutTransactionService transactionService;
	private final WithdrawalPayoutCoordinator coordinator;
	private final EthereumClient ethereumClient;
	private final TronClient tronClient;
	private final BscClient bscClient;
	private final EthereumProperties ethereumProperties;
	private final TronProperties tronProperties;
	private final BscProperties bscProperties;

	public WithdrawalPayoutConfirmationService(WithdrawalPayoutAttemptRepository attemptRepository,
			WithdrawalPayoutTransactionService transactionService, WithdrawalPayoutCoordinator coordinator,
			EthereumClient ethereumClient, TronClient tronClient, BscClient bscClient,
			EthereumProperties ethereumProperties, TronProperties tronProperties, BscProperties bscProperties) {
		this.attemptRepository = attemptRepository;
		this.transactionService = transactionService;
		this.coordinator = coordinator;
		this.ethereumClient = ethereumClient;
		this.tronClient = tronClient;
		this.bscClient = bscClient;
		this.ethereumProperties = ethereumProperties;
		this.tronProperties = tronProperties;
		this.bscProperties = bscProperties;
	}

	@Scheduled(fixedDelayString = "${reelshort.withdrawal.payout-confirmation-interval:30000}",
			initialDelayString = "${reelshort.withdrawal.payout-confirmation-initial-delay:30000}")
	public void processPending() {
		attemptRepository.findByStatusInOrderByUpdatedAtAsc(List.of(
				WithdrawalPayoutStatus.PREPARED, WithdrawalPayoutStatus.BROADCASTED))
				.forEach(attempt -> {
					try {
						processAttempt(attempt.id());
					}
					catch (RuntimeException exception) {
						log.warn("Payout confirmation scan failed for attempt {}: {}",
								attempt.id(), exception.getMessage());
					}
				});
	}

	public void processAttempt(UUID attemptId) {
		WithdrawalPayoutAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
		if (attempt == null || attempt.status() == WithdrawalPayoutStatus.CONFIRMED
				|| attempt.status() == WithdrawalPayoutStatus.FAILED_RETRYABLE
				|| attempt.status() == WithdrawalPayoutStatus.MANUAL_REVIEW) {
			return;
		}
		if (attempt.status() == WithdrawalPayoutStatus.PREPARED) {
			attempt = coordinator.retryBroadcast(attempt.id());
			if (attempt.status() == WithdrawalPayoutStatus.FAILED_RETRYABLE) {
				return;
			}
		}
		PayoutChainStatus chainStatus = queryChainStatus(attempt);
		if (chainStatus.state() == PayoutChainState.NOT_FOUND) {
			coordinator.retryBroadcast(attempt.id());
			return;
		}
		if (chainStatus.state() == PayoutChainState.UNKNOWN) {
			transactionService.recordBroadcastResult(attempt.id(),
					PayoutBroadcastResult.unknown(chainStatus.detail()));
			return;
		}
		if (chainStatus.state() == PayoutChainState.FAILED) {
			transactionService.recordChainObservation(attempt.id(), chainStatus);
			return;
		}
		int requiredConfirmations = requiredConfirmationsFor(attempt.network());
		if (chainStatus.state() == PayoutChainState.CONFIRMED
				&& chainStatus.confirmations() >= requiredConfirmations) {
			transactionService.settleConfirmed(attempt.id(), chainStatus.confirmations());
		}
		else {
			transactionService.recordChainObservation(attempt.id(), chainStatus);
		}
	}

	private PayoutChainStatus queryChainStatus(WithdrawalPayoutAttempt attempt) {
		String txHash = attempt.txHash();
		return switch (attempt.network()) {
			case "TRC20" -> tronClient.queryTransactionStatus(txHash);
			case "ERC20" -> ethereumClient.queryTransactionStatus(txHash);
			case "BEP20" -> bscClient.queryTransactionStatus(txHash);
			default -> throw new WithdrawalException(400, "unsupported withdrawal network");
		};
	}

	private int requiredConfirmationsFor(String network) {
		return switch (network) {
			case "TRC20" -> tronProperties.getRequiredConfirmations();
			case "ERC20" -> ethereumProperties.getRequiredConfirmations();
			case "BEP20" -> bscProperties.getRequiredConfirmations();
			default -> throw new WithdrawalException(400, "unsupported withdrawal network");
		};
	}
}
