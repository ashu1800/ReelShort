package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.reelshort.backend.points.PointAccount;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.PointTransactionRepository;

@SpringBootTest
@TestPropertySource(properties = {
		"reelshort.eth.required-confirmations=2",
		"reelshort.tron.required-confirmations=2",
		"reelshort.bsc.required-confirmations=2",
		"reelshort.tron.hot-wallet-address=TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA",
		"reelshort.eth.hot-wallet-address=0x2222222222222222222222222222222222222222",
		"reelshort.bsc.hot-wallet-address=0x3333333333333333333333333333333333333333",
		"reelshort.withdrawal.payout.unknown-max-attempts=2"
})
class WithdrawalPayoutIntegrationTests {

	@Autowired
	private WithdrawalPayoutConfirmationService confirmationService;
	@Autowired
	private WithdrawalPayoutCoordinator coordinator;
	@Autowired
	private HotWalletNonceAllocator nonceAllocator;
	@Autowired
	private HotWalletNonceRepository nonceRepository;
	@Autowired
	private WithdrawalPayoutTransactionService transactionService;
	@Autowired
	private WithdrawalPayoutAttemptRepository attemptRepository;
	@Autowired
	private WithdrawalRequestRepository withdrawalRepository;
	@Autowired
	private PointAccountRepository pointAccountRepository;
	@Autowired
	private PointTransactionRepository pointTransactionRepository;

	@MockitoBean
	private EthereumClient ethereumClient;
	@MockitoBean
	private TronClient tronClient;
	@MockitoBean
	private BscClient bscClient;

	@BeforeEach
	void clean() {
		attemptRepository.deleteAll();
		nonceRepository.deleteAll();
		withdrawalRepository.deleteAll();
		pointTransactionRepository.deleteAll();
		pointAccountRepository.deleteAll();
	}

	@Test
	void confirmationDoesNotDeductFrozenPointsBeforeRequiredDepth() {
		Fixture fixture = fixture();
		when(ethereumClient.queryTransactionStatus(fixture.attempt().txHash(), fixture.attempt().gasPrice()))
				.thenReturn(PayoutChainStatus.of(PayoutChainState.CONFIRMED, 1));

		confirmationService.processAttempt(fixture.attempt().id());

		PointAccount account = pointAccountRepository.findByUserId(fixture.userId()).orElseThrow();
		assertThat(account.balance()).isEqualTo(4000);
		assertThat(account.frozenPoints()).isEqualTo(3600);
		assertThat(pointTransactionRepository.findByIdempotencyKey("withdrawal:" + fixture.withdrawal().id()))
				.isEmpty();
	}

	@Test
	void confirmedAttemptSettlesFrozenPointsAndLedgerExactlyOnce() {
		Fixture fixture = fixture();
		when(ethereumClient.queryTransactionStatus(fixture.attempt().txHash(), fixture.attempt().gasPrice()))
				.thenReturn(PayoutChainStatus.of(PayoutChainState.CONFIRMED, 2));

		confirmationService.processAttempt(fixture.attempt().id());
		confirmationService.processAttempt(fixture.attempt().id());

		PointAccount account = pointAccountRepository.findByUserId(fixture.userId()).orElseThrow();
		assertThat(account.balance()).isEqualTo(400);
		assertThat(account.frozenPoints()).isZero();
		assertThat(pointTransactionRepository.findByIdempotencyKey("withdrawal:" + fixture.withdrawal().id()))
				.isPresent();
		assertThat(pointTransactionRepository.countByUserId(fixture.userId())).isEqualTo(1);
		assertThat(withdrawalRepository.findById(fixture.withdrawal().id()).orElseThrow().status())
				.isEqualTo(WithdrawalStatus.APPROVED);
	}

	@Test
	void confirmedAttemptPersistsActualFeeIdempotentlyAndRejectsConflictingFee() {
		Fixture fixture = fixture();
		PayoutChainStatus confirmed = PayoutChainStatus.confirmed(2, new BigDecimal("0.000021"), "ETH");

		transactionService.settleConfirmed(fixture.attempt().id(), confirmed);
		transactionService.recordChainObservation(fixture.attempt().id(), confirmed);

		WithdrawalPayoutAttempt stored = attemptRepository.findById(fixture.attempt().id()).orElseThrow();
		assertThat(stored.actualFeeAmount()).isEqualByComparingTo("0.000021");
		assertThat(stored.actualFeeAsset()).isEqualTo("ETH");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionService.recordChainObservation(
				fixture.attempt().id(), PayoutChainStatus.confirmed(3, new BigDecimal("0.000099"), "ETH")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("conflicts");
		WithdrawalPayoutAttempt unchanged = attemptRepository.findById(fixture.attempt().id()).orElseThrow();
		assertThat(unchanged.actualFeeAmount()).isEqualByComparingTo("0.000021");
		assertThat(unchanged.actualFeeAsset()).isEqualTo("ETH");
	}

	@Test
	void revertedReceiptReleasesActiveSlotForANewAttemptWithoutDeductingPoints() {
		Fixture fixture = fixture();
		when(ethereumClient.queryTransactionStatus(fixture.attempt().txHash(), fixture.attempt().gasPrice()))
				.thenReturn(PayoutChainStatus.of(PayoutChainState.FAILED, 0));

		confirmationService.processAttempt(fixture.attempt().id());

		WithdrawalPayoutAttempt failed = attemptRepository.findById(fixture.attempt().id()).orElseThrow();
		assertThat(failed.status()).isEqualTo(WithdrawalPayoutStatus.FAILED_RETRYABLE);
		assertThat(failed.activeSlot()).isNull();
		PointAccount account = pointAccountRepository.findByUserId(fixture.userId()).orElseThrow();
		assertThat(account.balance()).isEqualTo(4000);
		assertThat(account.frozenPoints()).isEqualTo(3600);
		assertThat(transactionService.findActive(fixture.withdrawal().id())).isEmpty();

		BigInteger gasPrice = BigInteger.valueOf(20_000_000_000L);
		WithdrawalPayoutAttempt retry = transactionService.reserveEthereum(fixture.withdrawal().id(), "retry-owner",
				"0x2222222222222222222222222222222222222222", BigInteger.valueOf(8), gasPrice, "admin");

		assertThat(retry.attemptNumber()).isEqualTo(2);
		assertThat(retry.status()).isEqualTo(WithdrawalPayoutStatus.SIGNING);
		assertThat(retry.activeSlot()).isEqualTo("ACTIVE");
	}

	@Test
	void coordinatorBroadcastsOnlyAfterPreparedAttemptTransactionCommits() {
		Fixture fixture = fixtureWithoutAttempt();
		String privateKey = "test-private-key";
		String hotWallet = "0x2222222222222222222222222222222222222222";
		PreparedPayoutTransaction signed = signed(BigInteger.valueOf(7), "a");
		when(ethereumClient.addressFromPrivateKey(privateKey)).thenReturn(hotWallet);
		when(ethereumClient.queryPendingNonce(hotWallet)).thenReturn(BigInteger.valueOf(7));
		when(ethereumClient.queryGasPrice()).thenReturn(BigInteger.valueOf(20_000_000_000L));
		when(ethereumClient.signTransfer(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(BigDecimal.class),
				org.mockito.ArgumentMatchers.any(BigInteger.class), org.mockito.ArgumentMatchers.any(BigInteger.class)))
				.thenReturn(signed);
		AtomicBoolean observedCommittedAttempt = new AtomicBoolean();
		when(ethereumClient.broadcastSignedTransaction(signed.signedRawTransaction(), signed.txHash()))
				.thenAnswer(invocation -> {
					observedCommittedAttempt.set(!TransactionSynchronizationManager.isActualTransactionActive()
							&& attemptRepository.findByWithdrawalRequestIdAndActiveSlot(
									fixture.withdrawal().id(), "ACTIVE").isPresent());
					return PayoutBroadcastResult.accepted();
				});

		coordinator.prepareAndBroadcast(fixture.withdrawal().id(), privateKey, "admin");

		assertThat(observedCommittedAttempt).isTrue();
	}

	@Test
	void databaseNonceAllocatorProducesUniqueNoncesForConcurrentWithdrawals() throws Exception {
		Fixture first = fixtureWithoutAttempt();
		Fixture second = fixtureWithoutAttempt();
		String hotWallet = "0x3333333333333333333333333333333333333333";
		BigInteger gasPrice = BigInteger.valueOf(20_000_000_000L);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<WithdrawalPayoutAttempt> firstResult = executor.submit(() -> transactionService.reserveEthereum(
					first.withdrawal().id(), "owner-1", hotWallet, BigInteger.valueOf(5), gasPrice, "admin"));
			Future<WithdrawalPayoutAttempt> secondResult = executor.submit(() -> transactionService.reserveEthereum(
					second.withdrawal().id(), "owner-2", hotWallet, BigInteger.valueOf(5), gasPrice, "admin"));

			assertThat(List.of(firstResult.get().nonce(), secondResult.get().nonce()))
					.containsExactlyInAnyOrder(BigInteger.valueOf(5), BigInteger.valueOf(6));
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void independentTransactionsInitializeOneNonceRowWithoutUniqueConflict() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> first = executor.submit(() -> nonceAllocator.ensureInitialized(
					"ERC20", "0x4444444444444444444444444444444444444444", 1L, BigInteger.valueOf(5)));
			Future<?> second = executor.submit(() -> nonceAllocator.ensureInitialized(
					"ERC20", "0x4444444444444444444444444444444444444444", 1L, BigInteger.valueOf(5)));

			first.get();
			second.get();
			assertThat(nonceRepository.count()).isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentEthereumPreparationSignsOnceOutsideTheReservationTransaction() throws Exception {
		Fixture fixture = fixtureWithoutAttempt();
		String privateKey = "ethereum-intent-key";
		String hotWallet = "0x2222222222222222222222222222222222222222";
		BigInteger gasPrice = BigInteger.valueOf(20_000_000_000L);
		AtomicInteger signingCount = new AtomicInteger();
		AtomicBoolean signedInsideTransaction = new AtomicBoolean();
		when(ethereumClient.addressFromPrivateKey(privateKey)).thenReturn(hotWallet);
		when(ethereumClient.queryPendingNonce(hotWallet)).thenReturn(BigInteger.valueOf(12));
		when(ethereumClient.queryGasPrice()).thenReturn(gasPrice);
		when(ethereumClient.signTransfer(org.mockito.ArgumentMatchers.eq(privateKey),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(BigDecimal.class),
				org.mockito.ArgumentMatchers.any(BigInteger.class), org.mockito.ArgumentMatchers.eq(gasPrice)))
				.thenAnswer(invocation -> {
					signingCount.incrementAndGet();
					signedInsideTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
					Thread.sleep(150);
					BigInteger nonce = invocation.getArgument(3);
					return new PreparedPayoutTransaction("ERC20", hotWallet,
							"0xdAC17F958D2ee523a2206206994597C13D831ec7", 1L, nonce,
							"0xsigned-" + nonce, "0x" + "e" + "0".repeat(63));
				});
		when(ethereumClient.broadcastSignedTransaction(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString())).thenReturn(PayoutBroadcastResult.accepted());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<WithdrawalPayoutAttempt> first = executor.submit(() -> coordinator.prepareAndBroadcast(
					fixture.withdrawal().id(), privateKey, "admin"));
			Future<WithdrawalPayoutAttempt> second = executor.submit(() -> coordinator.prepareAndBroadcast(
					fixture.withdrawal().id(), privateKey, "admin"));

			assertThat(List.of(first.get().id(), second.get().id())).containsOnly(first.get().id());
			assertThat(signingCount).hasValue(1);
			assertThat(signedInsideTransaction).isFalse();
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentTronPreparationCreatesOneIntentAndSignsOnce() throws Exception {
		Fixture fixture = fixtureWithoutAttempt("TRC20", "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE");
		String privateKey = "tron-intent-key";
		String hotWallet = "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA";
		AtomicInteger signingCount = new AtomicInteger();
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction("TRC20", hotWallet,
				"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", 0L, BigInteger.ZERO,
				"{\"txID\":\"tron-hash\"}", "tron-hash");
		when(tronClient.addressFromPrivateKey(privateKey)).thenReturn(hotWallet);
		when(tronClient.prepareTransfer(org.mockito.ArgumentMatchers.eq(privateKey),
				org.mockito.ArgumentMatchers.eq(fixture.withdrawal().walletAddress()),
				org.mockito.ArgumentMatchers.argThat(amount ->
						amount.compareTo(fixture.withdrawal().usdtAmount()) == 0))).thenAnswer(invocation -> {
				signingCount.incrementAndGet();
				Thread.sleep(150);
				return signed;
			});
		when(tronClient.broadcastSignedTransaction(signed.signedRawTransaction(), signed.txHash()))
				.thenReturn(PayoutBroadcastResult.accepted());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<WithdrawalPayoutAttempt> first = executor.submit(() -> coordinator.prepareAndBroadcast(
					fixture.withdrawal().id(), privateKey, "admin"));
			Future<WithdrawalPayoutAttempt> second = executor.submit(() -> coordinator.prepareAndBroadcast(
					fixture.withdrawal().id(), privateKey, "admin"));

			assertThat(List.of(first.get().id(), second.get().id())).containsOnly(first.get().id());
			assertThat(signingCount).hasValue(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void expiredTronReplayWithUnknownChainStateMovesToManualReviewWithoutReleasingSlot() {
		Fixture fixture = fixtureWithoutAttempt("TRC20", "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE");
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction("TRC20", "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA",
				"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", 0L, BigInteger.ZERO,
				"{\"txID\":\"expired-hash\"}", "expired-hash");
		WithdrawalPayoutAttempt attempt = WithdrawalPayoutAttempt.prepared(fixture.withdrawal().id(), 1,
				fixture.withdrawal().walletAddress(), fixture.withdrawal().usdtAmount(), signed, "admin");
		attemptRepository.saveAndFlush(attempt);
		when(tronClient.broadcastSignedTransaction(signed.signedRawTransaction(), signed.txHash()))
				.thenReturn(new PayoutBroadcastResult(PayoutBroadcastDisposition.EXPIRED, "expired"));
		when(tronClient.queryTransactionStatus(signed.txHash()))
				.thenReturn(PayoutChainStatus.unknown("node timeout"));

		WithdrawalPayoutAttempt result = coordinator.retryBroadcast(attempt.id());

		assertThat(result.status()).isEqualTo(WithdrawalPayoutStatus.MANUAL_REVIEW);
		assertThat(result.activeSlot()).isEqualTo("ACTIVE");
		assertThat(transactionService.findActive(fixture.withdrawal().id())).isPresent();
	}

	@Test
	void bep20ConfirmedAttemptSettlesFrozenPointsViaBscClient() {
		// BEP20 端到端：确认达标后通过 bscClient 查询链状态，幂等结算冻结积分。
		Fixture fixture = fixtureWithoutAttempt("BEP20", "0x1111111111111111111111111111111111111111");
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction("BEP20",
				"0x3333333333333333333333333333333333333333", "0x55d398326f99059fF775485246999027B3197955",
				56L, BigInteger.valueOf(3), "0xbsc-signed", "0xb" + "0".repeat(63));
		WithdrawalPayoutAttempt attempt = WithdrawalPayoutAttempt.prepared(fixture.withdrawal().id(), 1,
				fixture.withdrawal().walletAddress(), fixture.withdrawal().usdtAmount(), signed, "admin");
		attempt.markBroadcasted();
		attemptRepository.saveAndFlush(attempt);
		when(bscClient.queryTransactionStatus(signed.txHash(), attempt.gasPrice()))
				.thenReturn(PayoutChainStatus.of(PayoutChainState.CONFIRMED, 2));

		confirmationService.processAttempt(attempt.id());
		confirmationService.processAttempt(attempt.id());

		PointAccount account = pointAccountRepository.findByUserId(fixture.userId()).orElseThrow();
		assertThat(account.balance()).isEqualTo(400);
		assertThat(account.frozenPoints()).isZero();
		assertThat(withdrawalRepository.findById(fixture.withdrawal().id()).orElseThrow().status())
				.isEqualTo(WithdrawalStatus.APPROVED);
		// 确认走的是 bscClient 而非 ethereumClient（第二次 processAttempt 因已 CONFIRMED 提前返回，不再查链）。
		verify(bscClient, times(1)).queryTransactionStatus(signed.txHash(), attempt.gasPrice());
	}

	@Test
	void bep20NonceIsolatedFromErc20EvenWithSameWalletAddress() {
		// 验证 hot_wallet_nonces 唯一键含 network：同一地址在 BEP20 和 ERC20 下 nonce 行独立。
		String hotWallet = "0x5555555555555555555555555555555555555555";
		BigInteger gasPrice = BigInteger.valueOf(5_000_000_000L);
		Fixture bepFixture = fixtureWithoutAttempt("BEP20", "0x1111111111111111111111111111111111111111");
		Fixture ethFixture = fixtureWithoutAttempt("ERC20", "0x1111111111111111111111111111111111111111");

		transactionService.reserveBep20(bepFixture.withdrawal().id(), "owner", hotWallet,
				BigInteger.valueOf(10), gasPrice, "admin");
		transactionService.reserveEthereum(ethFixture.withdrawal().id(), "owner", hotWallet,
				BigInteger.valueOf(10), gasPrice, "admin");

		// 两条链各一条 nonce 行，互不影响。
		assertThat(nonceRepository.count()).isEqualTo(2L);
	}

	@Test
	void failedSigningIntentPersistenceDoesNotConsumeNonce() {
		Fixture failed = fixtureWithoutAttempt();
		String hotWallet = "0x6666666666666666666666666666666666666666";
		BigInteger observedNonce = BigInteger.valueOf(21);
		BigInteger gasPrice = BigInteger.valueOf(20_000_000_000L);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionService.reserveEthereum(
				failed.withdrawal().id(), "owner", hotWallet, observedNonce, gasPrice, "x".repeat(100)))
				.isInstanceOf(RuntimeException.class);

		Fixture successful = fixtureWithoutAttempt();
		WithdrawalPayoutAttempt attempt = transactionService.reserveEthereum(successful.withdrawal().id(),
				"owner", hotWallet, observedNonce, gasPrice, "admin");
		assertThat(attempt.nonce()).isEqualTo(observedNonce);
	}

	@Test
	void expiredTronReplayWithSingleNodeNotFoundMovesToManualReviewWithoutReleasingSlot() {
		Fixture fixture = fixtureWithoutAttempt("TRC20", "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE");
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction("TRC20", "TVjsyZ7fYF3qLF6BQgPmTEZy1xrNNyVAAA",
				"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", 0L, BigInteger.ZERO,
				"{\"txID\":\"expired-not-found\"}", "expired-not-found");
		WithdrawalPayoutAttempt attempt = WithdrawalPayoutAttempt.prepared(fixture.withdrawal().id(), 1,
				fixture.withdrawal().walletAddress(), fixture.withdrawal().usdtAmount(), signed, "admin");
		attemptRepository.saveAndFlush(attempt);
		when(tronClient.broadcastSignedTransaction(signed.signedRawTransaction(), signed.txHash()))
				.thenReturn(new PayoutBroadcastResult(PayoutBroadcastDisposition.EXPIRED, "expired"));
		when(tronClient.queryTransactionStatus(signed.txHash()))
				.thenReturn(PayoutChainStatus.of(PayoutChainState.NOT_FOUND, 0));

		WithdrawalPayoutAttempt result = coordinator.retryBroadcast(attempt.id());

		assertThat(result.status()).isEqualTo(WithdrawalPayoutStatus.MANUAL_REVIEW);
		assertThat(result.activeSlot()).isEqualTo("ACTIVE");
		assertThat(transactionService.findActive(fixture.withdrawal().id())).isPresent();
	}

	@Test
	void repeatedUnknownBroadcastEscalatesToManualReviewAtConfiguredThreshold() {
		Fixture fixture = fixture();
		PayoutBroadcastResult unknown = PayoutBroadcastResult.unknown("rpc timeout");

		transactionService.recordBroadcastResult(fixture.attempt().id(), unknown);
		WithdrawalPayoutAttempt escalated = transactionService.recordBroadcastResult(fixture.attempt().id(), unknown);

		assertThat(escalated.status()).isEqualTo(WithdrawalPayoutStatus.MANUAL_REVIEW);
		assertThat(escalated.activeSlot()).isEqualTo("ACTIVE");
		assertThat(escalated.unknownCount()).isEqualTo(2);
	}

	@Test
	void concurrentAttemptMutationsCompleteWithoutDeadlock() throws Exception {
		Fixture fixture = fixture();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> broadcast = executor.submit(() -> transactionService.recordBroadcastResult(
					fixture.attempt().id(), PayoutBroadcastResult.accepted()));
			Future<?> observation = executor.submit(() -> transactionService.recordChainObservation(
					fixture.attempt().id(), PayoutChainStatus.of(PayoutChainState.PENDING, 0)));

			broadcast.get(5, TimeUnit.SECONDS);
			observation.get(5, TimeUnit.SECONDS);
			assertThat(attemptRepository.findById(fixture.attempt().id())).isPresent();
		}
		finally {
			executor.shutdownNow();
		}
	}

	private Fixture fixture() {
		Fixture fixture = fixtureWithoutAttempt();
		WithdrawalPayoutAttempt attempt = WithdrawalPayoutAttempt.prepared(
				fixture.withdrawal().id(), 1, fixture.withdrawal().walletAddress(), fixture.withdrawal().usdtAmount(),
				signed(BigInteger.valueOf(7), "a"), "admin");
		attempt.markBroadcasted();
		attemptRepository.saveAndFlush(attempt);
		return new Fixture(fixture.userId(), fixture.withdrawal(), attempt);
	}

	private Fixture fixtureWithoutAttempt() {
		return fixtureWithoutAttempt("ERC20", "0x1111111111111111111111111111111111111111");
	}

	private Fixture fixtureWithoutAttempt(String network, String walletAddress) {
		UUID userId = UUID.randomUUID();
		PointAccount account = PointAccount.create(userId);
		account.add(4000);
		account.freeze(3600);
		pointAccountRepository.saveAndFlush(account);
		WithdrawalRequest withdrawal = WithdrawalRequest.create(userId, 3600, 0,
				new WithdrawalConversion(new BigDecimal("0.14"), 0),
				network, walletAddress);
		withdrawalRepository.saveAndFlush(withdrawal);
		return new Fixture(userId, withdrawal, null);
	}

	private PreparedPayoutTransaction signed(BigInteger nonce, String hashSeed) {
		String hash = (hashSeed + "0".repeat(64)).substring(0, 64);
		return new PreparedPayoutTransaction(
				"ERC20", "0x2222222222222222222222222222222222222222",
				"0xdAC17F958D2ee523a2206206994597C13D831ec7", 1L, nonce,
				"0xsigned-" + nonce, "0x" + hash);
	}

	private record Fixture(UUID userId, WithdrawalRequest withdrawal, WithdrawalPayoutAttempt attempt) {
	}
}
