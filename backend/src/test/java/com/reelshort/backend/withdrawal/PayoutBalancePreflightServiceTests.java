package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayoutBalancePreflightServiceTests {

	private final TronClient tronClient = mock(TronClient.class);
	private final EthereumClient ethereumClient = mock(EthereumClient.class);
	private final BscClient bscClient = mock(BscClient.class);
	private final TronProperties tronProperties = new TronProperties();
	private final EthereumProperties ethereumProperties = new EthereumProperties();
	private final BscProperties bscProperties = new BscProperties();
	private PayoutBalancePreflightService service;

	@BeforeEach
	void setUp() {
		service = new PayoutBalancePreflightService(tronClient, ethereumClient, bscClient,
				tronProperties, ethereumProperties, bscProperties);
	}

	@Test
	void rejectsTronBatchWhenAggregatedUsdtBalanceIsInsufficient() {
		WithdrawalRequest first = withdrawal("TRC20", "7.000000");
		WithdrawalRequest second = withdrawal("TRC20", "6.000000");
		when(tronClient.addressFromPrivateKey("tron-key")).thenReturn("THotWallet");
		when(tronClient.getUsdtBalance("THotWallet")).thenReturn(new BigDecimal("12.000000"));

		assertThatThrownBy(() -> service.requireSufficient(
				List.of(first, second), "tron-key", null, null))
				.isInstanceOf(WithdrawalException.class)
				.hasMessage("TRC20 USDT 余额不足：本次需要 13，当前余额 12，还差 1");
	}

	@Test
	void rejectsTronBatchWhenNativeFeeBalanceIsInsufficient() {
		WithdrawalRequest request = withdrawal("TRC20", "1.000000");
		when(tronClient.addressFromPrivateKey("tron-key")).thenReturn("THotWallet");
		when(tronClient.getUsdtBalance("THotWallet")).thenReturn(new BigDecimal("2.000000"));
		when(tronClient.getTrxBalance("THotWallet")).thenReturn(new BigDecimal("1.000000"));

		assertThatThrownBy(() -> service.requireSufficient(List.of(request), "tron-key", null, null))
				.isInstanceOf(WithdrawalException.class)
				.hasMessage("TRX 余额不足：本次需要 100，当前余额 1，还差 99");
	}

	private WithdrawalRequest withdrawal(String network, String amount) {
		WithdrawalRequest request = mock(WithdrawalRequest.class);
		when(request.id()).thenReturn(UUID.randomUUID());
		when(request.network()).thenReturn(network);
		when(request.usdtAmount()).thenReturn(new BigDecimal(amount));
		return request;
	}
}
