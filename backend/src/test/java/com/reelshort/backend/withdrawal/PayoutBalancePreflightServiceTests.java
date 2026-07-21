package com.reelshort.backend.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
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
		WithdrawalRequest first = withdrawal("TRC20", "1.000000");
		WithdrawalRequest second = withdrawal("TRC20", "1.000000");
		when(tronClient.addressFromPrivateKey("tron-key")).thenReturn("THotWallet");
		when(tronClient.getUsdtBalance("THotWallet")).thenReturn(new BigDecimal("20.000000"));
		when(tronClient.estimateTransferFees("THotWallet", List.of(first, second)))
				.thenReturn(new TronFeeQuote(new BigDecimal("31.269000"), 260570L, 0L, 20));
		when(tronClient.getTrxBalance("THotWallet")).thenReturn(new BigDecimal("19.969158"));

		assertThatThrownBy(() -> service.requireSufficient(
				List.of(first, second), "tron-key", null, null))
				.isInstanceOf(WithdrawalException.class)
				.hasMessage("TRX 预计手续费余额不足：本次预计需要 31.269，当前余额 19.969158，还差 11.299842");
		verify(tronClient).estimateTransferFees("THotWallet", List.of(first, second));
	}

	@Test
	void allowsTronBatchWhenDynamicFeeBalanceIsSufficient() {
		WithdrawalRequest first = withdrawal("TRC20", "1.000000");
		WithdrawalRequest second = withdrawal("TRC20", "1.000000");
		when(tronClient.addressFromPrivateKey("tron-key")).thenReturn("THotWallet");
		when(tronClient.getUsdtBalance("THotWallet")).thenReturn(new BigDecimal("20.000000"));
		when(tronClient.estimateTransferFees("THotWallet", List.of(first, second)))
				.thenReturn(new TronFeeQuote(new BigDecimal("31.269000"), 260570L, 0L, 20));
		when(tronClient.getTrxBalance("THotWallet")).thenReturn(new BigDecimal("32.000000"));

		service.requireSufficient(List.of(first, second), "tron-key", null, null);

		verify(tronClient).estimateTransferFees("THotWallet", List.of(first, second));
	}

	@Test
	void estimatesNativeFeesByNetworkAndTransactionCount() {
		ethereumProperties.setGasLimit(100_000L);
		bscProperties.setGasLimit(100_000L);
		when(ethereumClient.queryGasPrice()).thenReturn(new BigInteger("20000000000"));
		when(bscClient.queryGasPrice()).thenReturn(new BigInteger("3000000000"));

		List<PayoutFeeEstimate> estimates = service.estimateFees(List.of(
				withdrawal("BEP20", "1"),
				withdrawal("TRC20", "1"),
				withdrawal("ERC20", "1"),
				withdrawal("TRC20", "1")));

		assertThat(estimates).containsExactly(
				new PayoutFeeEstimate("TRC20", "TRX", 2, new BigDecimal("200"), "MAXIMUM"),
				new PayoutFeeEstimate("ERC20", "ETH", 1, new BigDecimal("0.002000000000000000"), "ESTIMATE"),
				new PayoutFeeEstimate("BEP20", "BNB", 1, new BigDecimal("0.000300000000000000"), "ESTIMATE"));
	}

	private WithdrawalRequest withdrawal(String network, String amount) {
		WithdrawalRequest request = mock(WithdrawalRequest.class);
		when(request.id()).thenReturn(UUID.randomUUID());
		when(request.network()).thenReturn(network);
		when(request.usdtAmount()).thenReturn(new BigDecimal(amount));
		return request;
	}
}
