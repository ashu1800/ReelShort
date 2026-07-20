package com.reelshort.backend.withdrawal;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.PointTransactionRepository;

class WithdrawalPayoutLockOrderTests {

	@Test
	void broadcastMutationLocksWithdrawalBeforeAttempt() {
		WithdrawalRequestRepository withdrawalRepository = mock(WithdrawalRequestRepository.class);
		WithdrawalPayoutAttemptRepository attemptRepository = mock(WithdrawalPayoutAttemptRepository.class);
		WithdrawalRequest withdrawal = mock(WithdrawalRequest.class);
		UUID withdrawalId = UUID.randomUUID();
		WithdrawalPayoutAttempt attempt = preparedAttempt(withdrawalId);
		when(attemptRepository.findById(attempt.id())).thenReturn(Optional.of(attempt));
		when(withdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
		when(attemptRepository.findByIdForUpdate(attempt.id())).thenReturn(Optional.of(attempt));

		service(withdrawalRepository, attemptRepository)
				.recordBroadcastResult(attempt.id(), PayoutBroadcastResult.accepted());

		InOrder locks = inOrder(withdrawalRepository, attemptRepository);
		locks.verify(attemptRepository).findById(attempt.id());
		locks.verify(withdrawalRepository).findByIdForUpdate(withdrawalId);
		locks.verify(attemptRepository).findByIdForUpdate(attempt.id());
	}

	private WithdrawalPayoutTransactionService service(WithdrawalRequestRepository withdrawalRepository,
			WithdrawalPayoutAttemptRepository attemptRepository) {
		EthereumProperties ethereum = new EthereumProperties();
		return new WithdrawalPayoutTransactionService(withdrawalRepository, attemptRepository,
				mock(HotWalletNonceAllocator.class), mock(PointAccountRepository.class),
				mock(PointTransactionRepository.class), ethereum, new TronProperties(), new BscProperties(),
				new WithdrawalPayoutProperties());
	}

	private WithdrawalPayoutAttempt preparedAttempt(UUID withdrawalId) {
		PreparedPayoutTransaction signed = new PreparedPayoutTransaction("ERC20",
				"0x2222222222222222222222222222222222222222",
				"0xdAC17F958D2ee523a2206206994597C13D831ec7", 1L, BigInteger.ONE,
				"0xsigned", "0x" + "a".repeat(64));
		return WithdrawalPayoutAttempt.prepared(withdrawalId, 1,
				"0x1111111111111111111111111111111111111111", new BigDecimal("10"), signed, "admin");
	}
}
