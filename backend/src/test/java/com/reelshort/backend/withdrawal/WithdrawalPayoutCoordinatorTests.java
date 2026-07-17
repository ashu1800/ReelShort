package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalPayoutCoordinatorTests {

	private static final UUID WITHDRAWAL_ID = UUID.randomUUID();
	private static final String RAW_TRANSACTION = "0xsigned-once";
	private static final String TX_HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

	@Mock
	private WithdrawalPayoutTransactionService transactionService;
	@Mock
	private WithdrawalRequestRepository withdrawalRequestRepository;
	@Mock
	private EthereumClient ethereumClient;
	@Mock
	private TronClient tronClient;

	private WithdrawalPayoutCoordinator coordinator;

	@BeforeEach
	void setUp() {
		coordinator = new WithdrawalPayoutCoordinator(
				transactionService, withdrawalRequestRepository, ethereumClient, tronClient);
	}

	@Test
	void completedWithdrawalIsIdempotentAndReturnsConfirmedAttemptWithoutBroadcast() {
		WithdrawalPayoutAttempt confirmed = preparedAttempt();
		confirmed.markConfirmed(12);
		WithdrawalRequest request = org.mockito.Mockito.mock(WithdrawalRequest.class);
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.empty());
		when(transactionService.findLatestConfirmed(WITHDRAWAL_ID)).thenReturn(Optional.of(confirmed));
		when(withdrawalRequestRepository.findById(WITHDRAWAL_ID)).thenReturn(Optional.of(request));
		when(request.status()).thenReturn(WithdrawalStatus.APPROVED);

		assertThat(coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "must-not-be-used", "admin"))
				.isSameAs(confirmed);
		verify(ethereumClient, never()).broadcastSignedTransaction(any(), any());
		verify(tronClient, never()).broadcastSignedTransaction(any(), any());
		verify(ethereumClient, never()).signTransfer(any(), any(), any(), any(), any());
	}

	@Test
	void activePreparedAttemptReplaysTheSameRawTransactionWithoutSigningAgain() {
		WithdrawalPayoutAttempt attempt = preparedAttempt();
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.of(attempt));
		when(ethereumClient.broadcastSignedTransaction(RAW_TRANSACTION, TX_HASH))
				.thenReturn(PayoutBroadcastResult.accepted());

		WithdrawalPayoutAttempt result = coordinator.prepareAndBroadcast(
				WITHDRAWAL_ID, "private-key-must-not-be-used", "admin");

		assertThat(result.id()).isEqualTo(attempt.id());
		verify(ethereumClient).broadcastSignedTransaction(RAW_TRANSACTION, TX_HASH);
		verify(ethereumClient, never()).signTransfer(any(), any(), any(), any(), any());
		verify(tronClient, never()).prepareTransfer(any(), any(), any());
	}

	@Test
	void rpcTimeoutKeepsAttemptActiveAndPreventsNewTransaction() {
		WithdrawalPayoutAttempt attempt = preparedAttempt();
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.of(attempt));
		PayoutBroadcastResult unknown = PayoutBroadcastResult.unknown("timeout");
		when(ethereumClient.broadcastSignedTransaction(RAW_TRANSACTION, TX_HASH)).thenReturn(unknown);
		when(transactionService.recordBroadcastResult(attempt.id(), unknown)).thenReturn(attempt);

		coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "unused", "admin");

		verify(transactionService).recordBroadcastResult(attempt.id(), unknown);
		verify(transactionService, never()).prepareEthereum(any(), any(), any(), any(), any(), any());
	}

	@Test
	void manualReviewAttemptCannotBeAutomaticallyReplayedOrResigned() {
		WithdrawalPayoutAttempt attempt = preparedAttempt();
		attempt.markManualReview("ambiguous chain response");
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.of(attempt));

		assertThatThrownBy(() -> coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "unused", "admin"))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("manual review");
		verify(ethereumClient, never()).broadcastSignedTransaction(any(), any());
	}

	private WithdrawalPayoutAttempt preparedAttempt() {
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction(
				"ERC20", "0x2222222222222222222222222222222222222222",
				"0xdAC17F958D2ee523a2206206994597C13D831ec7", 1L, BigInteger.valueOf(8),
				RAW_TRANSACTION, TX_HASH);
		return WithdrawalPayoutAttempt.prepared(
				WITHDRAWAL_ID, 1, "0x1111111111111111111111111111111111111111",
				new BigDecimal("10.000000"), signed, "admin");
	}
}
