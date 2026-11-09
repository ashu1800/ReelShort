package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.points.PointAccount;
import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.PointTransaction;
import com.reelshort.backend.points.PointTransactionRepository;
import com.reelshort.backend.wallet.UserWallet;
import com.reelshort.backend.wallet.UserWalletRepository;

/**
 * 用户提交提现 → 管理员人工确认 → 扣除冻结积分这条主链路的账务回归。
 *
 * <p>打款执行能力默认关闭，所以自动打款路径（{@code WithdrawalPayoutIntegrationTests}）不会覆盖
 * 「提交/确认/拒绝」本身；本测试直接走 {@link WithdrawalService}，锁住三件事：
 * 提交只冻结不扣款、人工确认只扣一次且写负数 WITHDRAWAL 流水、拒绝只解冻不扣款。
 * 默认换算：每 50 积分 0.14 USDT、最低 10 USDT、手续费 10%。4000 积分折算 11.20 USDT，
 * 手续费从 USDT 里扣，所以到账 10.08 USDT（扣减积分仍是 4000）。
 */
@SpringBootTest
class WithdrawalServiceFlowTests {

	private static final int POINTS = 4000;
	private static final String WALLET = "0x1111111111111111111111111111111111111111";

	@Autowired
	private WithdrawalService withdrawalService;
	@Autowired
	private WithdrawalRequestRepository withdrawalRepository;
	@Autowired
	private PointAccountRepository pointAccountRepository;
	@Autowired
	private PointTransactionRepository pointTransactionRepository;
	@Autowired
	private UserWalletRepository userWalletRepository;

	private UUID userId;
	private PointAccount account;

	@BeforeEach
	void setUp() {
		withdrawalRepository.deleteAll();
		pointTransactionRepository.deleteAll();
		pointAccountRepository.deleteAll();
		userWalletRepository.deleteAll();
		userId = UUID.randomUUID();
		PointAccount created = PointAccount.create(userId);
		created.add(5000);
		account = pointAccountRepository.saveAndFlush(created);
		userWalletRepository.saveAndFlush(UserWallet.create(userId, "ERC20", WALLET));
	}

	@Test
	void submitFreezesPointsWithoutDeductingTheBalance() {
		WithdrawalResponse response = withdrawalService.create(userId, POINTS);

		assertThat(response.status()).isEqualTo(WithdrawalStatus.PENDING);
		assertThat(response.usdtAmount()).isEqualTo("10.08");
		PointAccount stored = pointAccountRepository.findByUserId(userId).orElseThrow();
		assertThat(stored.balance()).isEqualTo(5000);
		assertThat(stored.frozenPoints()).isEqualTo(POINTS);
		assertThat(stored.availablePoints()).isEqualTo(1000);
		assertThat(pointTransactionRepository.countByUserId(userId)).isZero();
	}

	@Test
	void theDynamicMinimumIsReachedByTheTenUsdtGrossAmount() {
		// 10 USDT 折算总额 = 3572 积分（3572 * 0.14 / 50 = 10.00），少于 1 分即低于门槛。
		assertThatThrownBy(() -> withdrawalService.create(userId, 3571))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("below minimum");
		// 3571 被拒时不能冻结任何积分，用户还能继续提交。
		assertThat(pointAccountRepository.findByUserId(userId).orElseThrow().frozenPoints()).isZero();

		WithdrawalResponse response = withdrawalService.create(userId, 3572);
		assertThat(response.pointAmount()).isEqualTo(3572);
		// 手续费从 USDT 里扣：10.00 - 10% = 9.00，扣减积分仍是 3572。
		assertThat(response.usdtAmount()).isEqualTo("9");
		assertThat(pointAccountRepository.findByUserId(userId).orElseThrow().frozenPoints()).isEqualTo(3572);
	}

	@Test
	void submitRejectsASecondPendingRequestAndAmountsBelowTheDynamicMinimum() {
		withdrawalService.create(userId, POINTS);

		assertThatThrownBy(() -> withdrawalService.create(userId, POINTS))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("already pending");
		assertThat(pointAccountRepository.findByUserId(userId).orElseThrow().frozenPoints())
				.isEqualTo(POINTS);
	}

	@Test
	void manualConfirmDeductsFrozenPointsOnceAndWritesTheLedgerEntry() {
		WithdrawalResponse submitted = withdrawalService.create(userId, POINTS);

		withdrawalService.manualConfirm(submitted.id(), "admin");
		// 重复确认（管理员重复点击/超时重试）必须幂等
		withdrawalService.manualConfirm(submitted.id(), "admin");

		PointAccount stored = pointAccountRepository.findByUserId(userId).orElseThrow();
		assertThat(stored.balance()).isEqualTo(1000);
		assertThat(stored.frozenPoints()).isZero();
		assertThat(withdrawalRepository.findById(submitted.id()).orElseThrow().status())
				.isEqualTo(WithdrawalStatus.APPROVED);

		List<PointTransaction> ledger = pointTransactionRepository
				.findAll().stream().filter(tx -> "WITHDRAWAL".equals(tx.source())).toList();
		assertThat(ledger).hasSize(1);
		assertThat(ledger.get(0).idempotencyKey()).isEqualTo("withdrawal:" + submitted.id());
		assertThat(ledger.get(0).amount()).isEqualTo(-POINTS);
		assertThat(ledger.get(0).balanceAfter()).isEqualTo(1000);
		// 余额 = 期初余额 + 流水累计（期初 5000 是测试夹具直接 add 的，不带流水行）
		assertThat(pointTransactionRepository.findAll().stream().mapToInt(PointTransaction::amount).sum())
				.isEqualTo(stored.balance() - 5000);
	}

	@Test
	void rejectReleasesFrozenPointsWithoutTouchingTheBalanceOrTheLedger() {
		WithdrawalResponse submitted = withdrawalService.create(userId, POINTS);

		withdrawalService.reject(submitted.id(), "wallet mismatch", "admin");

		PointAccount stored = pointAccountRepository.findByUserId(userId).orElseThrow();
		assertThat(stored.balance()).isEqualTo(5000);
		assertThat(stored.frozenPoints()).isZero();
		assertThat(withdrawalRepository.findById(submitted.id()).orElseThrow().status())
				.isEqualTo(WithdrawalStatus.REJECTED);
		assertThat(pointTransactionRepository.countByUserId(userId)).isZero();
		// 拒绝后用户可以重新提交
		assertThat(withdrawalService.create(userId, POINTS).status()).isEqualTo(WithdrawalStatus.PENDING);
	}

	@Test
	void manualConfirmRecordsTheOptionalPayoutTransactionHash() {
		WithdrawalResponse submitted = withdrawalService.create(userId, POINTS);
		String txHash = "a".repeat(64);

		withdrawalService.manualConfirm(submitted.id(), txHash.toUpperCase(java.util.Locale.ROOT), "admin");

		assertThat(withdrawalRepository.findById(submitted.id()).orElseThrow().txHash()).isEqualTo(txHash);
		// 同一个链上哈希不允许落到第二笔提现上
		UUID otherUser = UUID.randomUUID();
		PointAccount otherAccount = PointAccount.create(otherUser);
		otherAccount.add(5000);
		pointAccountRepository.saveAndFlush(otherAccount);
		userWalletRepository.saveAndFlush(UserWallet.create(otherUser, "ERC20", WALLET));
		WithdrawalResponse second = withdrawalService.create(otherUser, POINTS);
		assertThatThrownBy(() -> withdrawalService.manualConfirm(second.id(), txHash, "admin"))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("already recorded");
		assertThatThrownBy(() -> withdrawalService.manualConfirm(second.id(), "not-hex", "admin"))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("64 hex");
	}

	@Test
	void stalePendingWithdrawalIsAutoRejectedAndUnfrozen() {
		WithdrawalResponse submitted = withdrawalService.create(userId, POINTS);

		// 超时阈值默认 72h，所以刚提交的申请不在待过期集合里
		assertThat(withdrawalService.stalePendingWithdrawalIds()).isEmpty();

		withdrawalService.expireStale(submitted.id());

		PointAccount stored = pointAccountRepository.findByUserId(userId).orElseThrow();
		assertThat(stored.balance()).isEqualTo(5000);
		assertThat(stored.frozenPoints()).isZero();
		WithdrawalRequest expired = withdrawalRepository.findById(submitted.id()).orElseThrow();
		assertThat(expired.status()).isEqualTo(WithdrawalStatus.REJECTED);
		assertThat(expired.adminNote()).contains("auto rejected");
		assertThat(pointTransactionRepository.countByUserId(userId)).isZero();
		// 释放后用户可以重新提交
		assertThat(withdrawalService.create(userId, POINTS).status()).isEqualTo(WithdrawalStatus.PENDING);
	}

	@Test
	void submitRequiresAnErc20WalletAndEnoughAvailablePoints() {
		userWalletRepository.deleteAll();
		assertThatThrownBy(() -> withdrawalService.create(userId, POINTS))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("wallet required");

		userWalletRepository.saveAndFlush(UserWallet.create(userId, "ERC20", WALLET));
		assertThatThrownBy(() -> withdrawalService.create(userId, 6000))
				.isInstanceOf(AdminException.class)
				.hasMessageContaining("insufficient available point balance");
	}
}
