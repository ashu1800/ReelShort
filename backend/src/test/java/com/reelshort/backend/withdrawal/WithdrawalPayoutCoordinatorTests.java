package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
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
	@Mock
	private BscClient bscClient;

	private WithdrawalPayoutCoordinator coordinator;
	private EthereumProperties ethereumProperties;
	private TronProperties tronProperties;
	private BscProperties bscProperties;

	@BeforeEach
	void setUp() {
		ethereumProperties = new EthereumProperties();
		ethereumProperties.setHotWalletAddress("0x2222222222222222222222222222222222222222");
		tronProperties = new TronProperties();
		tronProperties.setHotWalletAddress("TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA");
		bscProperties = new BscProperties();
		bscProperties.setHotWalletAddress("0x3333333333333333333333333333333333333333");
		coordinator = new WithdrawalPayoutCoordinator(
				transactionService, withdrawalRequestRepository, ethereumClient, tronClient, bscClient,
				ethereumProperties, tronProperties, bscProperties);
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
		when(transactionService.recordBroadcastResult(attempt.id(), PayoutBroadcastResult.accepted()))
				.thenReturn(attempt);

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
		verify(transactionService, never()).reserveEthereum(any(), any(), any(), any(), any(), any());
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

	@Test
	void emptyExpectedHotWalletAllowsEthereumAttemptCreation() {
		WithdrawalRequest request = org.mockito.Mockito.mock(WithdrawalRequest.class);
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.empty());
		when(withdrawalRequestRepository.findById(WITHDRAWAL_ID)).thenReturn(Optional.of(request));
		when(request.status()).thenReturn(WithdrawalStatus.PENDING);
		when(request.network()).thenReturn("ERC20");
		when(ethereumClient.addressFromPrivateKey("private-key"))
				.thenReturn("0x2222222222222222222222222222222222222222");
		when(ethereumClient.queryPendingNonce("0x2222222222222222222222222222222222222222"))
				.thenReturn(BigInteger.valueOf(8));
		when(ethereumClient.queryGasPrice()).thenReturn(BigInteger.valueOf(9));
		WithdrawalPayoutAttempt prepared = preparedAttempt();
		when(transactionService.reserveEthereum(eq(WITHDRAWAL_ID), anyString(),
				eq("0x2222222222222222222222222222222222222222"), eq(BigInteger.valueOf(8)),
				eq(BigInteger.valueOf(9)), eq("admin"))).thenReturn(prepared);
		when(ethereumClient.broadcastSignedTransaction(RAW_TRANSACTION, TX_HASH))
				.thenReturn(PayoutBroadcastResult.accepted());
		when(transactionService.recordBroadcastResult(prepared.id(), PayoutBroadcastResult.accepted()))
				.thenReturn(prepared);
		ethereumProperties.setHotWalletAddress("");

		assertThat(coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "private-key", "admin"))
				.isSameAs(prepared);
		verify(transactionService).reserveEthereum(eq(WITHDRAWAL_ID), anyString(),
				eq("0x2222222222222222222222222222222222222222"), eq(BigInteger.valueOf(8)),
				eq(BigInteger.valueOf(9)), eq("admin"));
	}

	@Test
	void mismatchedExpectedHotWalletFailsClosedBeforeCreatingAttempt() {
		WithdrawalRequest request = org.mockito.Mockito.mock(WithdrawalRequest.class);
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.empty());
		when(withdrawalRequestRepository.findById(WITHDRAWAL_ID)).thenReturn(Optional.of(request));
		when(request.status()).thenReturn(WithdrawalStatus.PENDING);
		when(request.network()).thenReturn("TRC20");
		when(tronClient.addressFromPrivateKey("private-key"))
				.thenReturn("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE");

		assertThatThrownBy(() -> coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "private-key", "admin"))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("does not match configured hot wallet");
		verify(transactionService, never()).reserveTron(any(), any(), any(), any());
	}

	@Test
	void emptyExpectedHotWalletAllowsBep20AttemptCreation() {
		WithdrawalRequest request = org.mockito.Mockito.mock(WithdrawalRequest.class);
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.empty());
		when(withdrawalRequestRepository.findById(WITHDRAWAL_ID)).thenReturn(Optional.of(request));
		when(request.status()).thenReturn(WithdrawalStatus.PENDING);
		when(request.network()).thenReturn("BEP20");
		when(bscClient.addressFromPrivateKey("private-key"))
				.thenReturn("0x3333333333333333333333333333333333333333");
		when(bscClient.queryPendingNonce("0x3333333333333333333333333333333333333333"))
				.thenReturn(BigInteger.valueOf(8));
		when(bscClient.queryGasPrice()).thenReturn(BigInteger.valueOf(9));
		WithdrawalPayoutAttempt prepared = preparedAttempt(
				"BEP20", "0x3333333333333333333333333333333333333333");
		when(transactionService.reserveBep20(eq(WITHDRAWAL_ID), anyString(),
				eq("0x3333333333333333333333333333333333333333"), eq(BigInteger.valueOf(8)),
				eq(BigInteger.valueOf(9)), eq("admin"))).thenReturn(prepared);
		when(bscClient.broadcastSignedTransaction(RAW_TRANSACTION, TX_HASH))
				.thenReturn(PayoutBroadcastResult.accepted());
		when(transactionService.recordBroadcastResult(prepared.id(), PayoutBroadcastResult.accepted()))
				.thenReturn(prepared);
		bscProperties.setHotWalletAddress("");

		assertThat(coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "private-key", "admin"))
				.isSameAs(prepared);
		verify(transactionService).reserveBep20(eq(WITHDRAWAL_ID), anyString(),
				eq("0x3333333333333333333333333333333333333333"), eq(BigInteger.valueOf(8)),
				eq(BigInteger.valueOf(9)), eq("admin"));
	}

	@Test
	void emptyExpectedHotWalletAllowsTronAttemptCreation() {
		WithdrawalRequest request = org.mockito.Mockito.mock(WithdrawalRequest.class);
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.empty());
		when(withdrawalRequestRepository.findById(WITHDRAWAL_ID)).thenReturn(Optional.of(request));
		when(request.status()).thenReturn(WithdrawalStatus.PENDING);
		when(request.network()).thenReturn("TRC20");
		String address = "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA";
		when(tronClient.addressFromPrivateKey("private-key")).thenReturn(address);
		WithdrawalPayoutAttempt prepared = preparedAttempt("TRC20", address);
		when(transactionService.reserveTron(eq(WITHDRAWAL_ID), anyString(), eq(address), eq("admin")))
				.thenReturn(prepared);
		when(tronClient.broadcastSignedTransaction(RAW_TRANSACTION, TX_HASH))
				.thenReturn(PayoutBroadcastResult.accepted());
		when(transactionService.recordBroadcastResult(prepared.id(), PayoutBroadcastResult.accepted()))
				.thenReturn(prepared);
		tronProperties.setHotWalletAddress("");

		assertThat(coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "private-key", "admin"))
				.isSameAs(prepared);
		verify(transactionService).reserveTron(eq(WITHDRAWAL_ID), anyString(), eq(address), eq("admin"));
	}

	@Test
	void emptyExpectedHotWalletAllowsSigningIntentOwnedByDerivedAddress() {
		String address = "0x2222222222222222222222222222222222222222";
		WithdrawalPayoutAttempt signing = WithdrawalPayoutAttempt.signingIntent(
				WITHDRAWAL_ID, 1, "ERC20", address,
				"0x1111111111111111111111111111111111111111",
				"0xdAC17F958D2ee523a2206206994597C13D831ec7",
				new BigDecimal("10.000000"), 1L, BigInteger.valueOf(8), BigInteger.valueOf(9),
				"expired-owner", "admin", Duration.ZERO);
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction(
				"ERC20", address, "0xdAC17F958D2ee523a2206206994597C13D831ec7",
				1L, BigInteger.valueOf(8), RAW_TRANSACTION, TX_HASH);
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.of(signing));
		when(transactionService.claimSigning(eq(signing.id()), anyString())).thenAnswer(invocation -> {
			String owner = invocation.getArgument(1);
			signing.claimSigning(owner, Duration.ofMinutes(1));
			return signing;
		});
		when(ethereumClient.addressFromPrivateKey("private-key")).thenReturn(address);
		when(ethereumClient.signTransfer("private-key", signing.destinationAddress(), signing.tokenAmount(),
				signing.nonce(), signing.gasPrice())).thenReturn(signed);
		when(transactionService.completeSigning(eq(signing.id()), anyString(), eq(signed)))
				.thenAnswer(invocation -> {
					String owner = invocation.getArgument(1);
					signing.completeSigning(owner, signed);
					return signing;
				});
		when(ethereumClient.broadcastSignedTransaction(RAW_TRANSACTION, TX_HASH))
				.thenReturn(PayoutBroadcastResult.accepted());
		when(transactionService.recordBroadcastResult(signing.id(), PayoutBroadcastResult.accepted()))
				.thenReturn(signing);
		ethereumProperties.setHotWalletAddress("");

		assertThat(coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "private-key", "admin"))
				.isSameAs(signing);
	}

	@Test
	void emptyExpectedHotWalletRejectsSigningIntentOwnedByAnotherDerivedAddress() {
		WithdrawalPayoutAttempt signing = WithdrawalPayoutAttempt.signingIntent(
				WITHDRAWAL_ID, 1, "ERC20", "0x2222222222222222222222222222222222222222",
				"0x1111111111111111111111111111111111111111",
				"0xdAC17F958D2ee523a2206206994597C13D831ec7",
				new BigDecimal("10.000000"), 1L, BigInteger.valueOf(8), BigInteger.valueOf(9),
				"expired-owner", "admin", Duration.ZERO);
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.of(signing));
		when(transactionService.claimSigning(eq(signing.id()), anyString())).thenAnswer(invocation -> {
			String owner = invocation.getArgument(1);
			signing.claimSigning(owner, Duration.ofMinutes(1));
			return signing;
		});
		when(ethereumClient.addressFromPrivateKey("private-key"))
				.thenReturn("0x4444444444444444444444444444444444444444");
		ethereumProperties.setHotWalletAddress("");

		assertThatThrownBy(() -> coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "private-key", "admin"))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("payout signing intent");
		verify(ethereumClient, never()).signTransfer(any(), any(), any(), any(), any());
	}

	@Test
	void bep20MismatchedHotWalletFailsClosedBeforeCreatingAttempt() {
		WithdrawalRequest request = org.mockito.Mockito.mock(WithdrawalRequest.class);
		when(transactionService.findActive(WITHDRAWAL_ID)).thenReturn(Optional.empty());
		when(withdrawalRequestRepository.findById(WITHDRAWAL_ID)).thenReturn(Optional.of(request));
		when(request.status()).thenReturn(WithdrawalStatus.PENDING);
		when(request.network()).thenReturn("BEP20");
		when(bscClient.addressFromPrivateKey("private-key"))
				.thenReturn("0x4444444444444444444444444444444444444444");

		assertThatThrownBy(() -> coordinator.prepareAndBroadcast(WITHDRAWAL_ID, "private-key", "admin"))
				.isInstanceOf(WithdrawalException.class)
				.hasMessageContaining("does not match configured hot wallet");
		verify(transactionService, never()).reserveBep20(any(), any(), any(), any(), any(), any());
	}

	private WithdrawalPayoutAttempt preparedAttempt() {
		return preparedAttempt("ERC20", "0x2222222222222222222222222222222222222222");
	}

	private WithdrawalPayoutAttempt preparedAttempt(String network, String hotWalletAddress) {
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction(
				network, hotWalletAddress,
				"0xdAC17F958D2ee523a2206206994597C13D831ec7", 1L, BigInteger.valueOf(8),
				RAW_TRANSACTION, TX_HASH);
		return WithdrawalPayoutAttempt.prepared(
				WITHDRAWAL_ID, 1, "0x1111111111111111111111111111111111111111",
				new BigDecimal("10.000000"), signed, "admin");
	}
}
