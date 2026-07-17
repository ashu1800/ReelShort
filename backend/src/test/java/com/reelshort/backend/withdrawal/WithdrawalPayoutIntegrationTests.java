package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

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
		"reelshort.tron.required-confirmations=2"
})
class WithdrawalPayoutIntegrationTests {

	@Autowired
	private WithdrawalPayoutConfirmationService confirmationService;
	@Autowired
	private WithdrawalPayoutCoordinator coordinator;
	@Autowired
	private HotWalletNonceAllocator nonceAllocator;
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

	@BeforeEach
	void clean() {
		attemptRepository.deleteAll();
		withdrawalRepository.deleteAll();
		pointTransactionRepository.deleteAll();
		pointAccountRepository.deleteAll();
	}

	@Test
	void confirmationDoesNotDeductFrozenPointsBeforeRequiredDepth() {
		Fixture fixture = fixture();
		when(ethereumClient.queryTransactionStatus(fixture.attempt().txHash()))
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
		when(ethereumClient.queryTransactionStatus(fixture.attempt().txHash()))
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
	void revertedReceiptReleasesActiveSlotForANewAttemptWithoutDeductingPoints() {
		Fixture fixture = fixture();
		when(ethereumClient.queryTransactionStatus(fixture.attempt().txHash()))
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
		PreparedPayoutTransaction retrySigned = signed(BigInteger.valueOf(8), "b");
		when(ethereumClient.signTransfer("retry-key", fixture.withdrawal().walletAddress(),
				fixture.withdrawal().usdtAmount(), BigInteger.valueOf(8), gasPrice)).thenReturn(retrySigned);
		WithdrawalPayoutAttempt retry = transactionService.prepareEthereum(fixture.withdrawal().id(), "retry-key",
				retrySigned.hotWalletAddress(), BigInteger.valueOf(8), gasPrice, "admin");

		assertThat(retry.attemptNumber()).isEqualTo(2);
		assertThat(retry.status()).isEqualTo(WithdrawalPayoutStatus.PREPARED);
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
		String privateKey = "concurrent-key";
		String hotWallet = "0x3333333333333333333333333333333333333333";
		BigInteger gasPrice = BigInteger.valueOf(20_000_000_000L);
		when(ethereumClient.signTransfer(
				org.mockito.ArgumentMatchers.eq(privateKey), org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(BigDecimal.class), org.mockito.ArgumentMatchers.any(BigInteger.class),
				org.mockito.ArgumentMatchers.eq(gasPrice)))
				.thenAnswer(invocation -> {
					BigInteger nonce = invocation.getArgument(3);
					String hash = UUID.randomUUID().toString().replace("-", "") + "0".repeat(32);
					return new PreparedPayoutTransaction("ERC20", hotWallet,
							"0xdAC17F958D2ee523a2206206994597C13D831ec7", 1L, nonce,
							"0xsigned-" + nonce, "0x" + hash);
				});
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<WithdrawalPayoutAttempt> firstResult = executor.submit(() -> transactionService.prepareEthereum(
					first.withdrawal().id(), privateKey, hotWallet, BigInteger.valueOf(5), gasPrice, "admin"));
			Future<WithdrawalPayoutAttempt> secondResult = executor.submit(() -> transactionService.prepareEthereum(
					second.withdrawal().id(), privateKey, hotWallet, BigInteger.valueOf(5), gasPrice, "admin"));

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
			Future<BigInteger> first = executor.submit(() -> nonceAllocator.allocate(
					"ERC20", "0x4444444444444444444444444444444444444444", 1L, BigInteger.valueOf(5)));
			Future<BigInteger> second = executor.submit(() -> nonceAllocator.allocate(
					"ERC20", "0x4444444444444444444444444444444444444444", 1L, BigInteger.valueOf(5)));

			assertThat(List.of(first.get(), second.get()))
					.containsExactlyInAnyOrder(BigInteger.valueOf(5), BigInteger.valueOf(6));
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
		UUID userId = UUID.randomUUID();
		PointAccount account = PointAccount.create(userId);
		account.add(4000);
		account.freeze(3600);
		pointAccountRepository.saveAndFlush(account);
		WithdrawalRequest withdrawal = WithdrawalRequest.create(userId, 3600, 0,
				new WithdrawalConversion(new BigDecimal("0.02"), new BigDecimal("7.2"), new BigDecimal("10")),
				"ERC20", "0x1111111111111111111111111111111111111111");
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
