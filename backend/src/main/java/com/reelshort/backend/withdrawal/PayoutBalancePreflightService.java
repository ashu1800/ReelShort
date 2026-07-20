package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Checks token and native fee balances before any payout attempt is created. */
@Service
public class PayoutBalancePreflightService {

	private static final BigDecimal TRX_PER_SUN = new BigDecimal("1000000");
	private static final BigDecimal NATIVE_DECIMALS = new BigDecimal("1000000000000000000");

	private final TronClient tronClient;
	private final EthereumClient ethereumClient;
	private final BscClient bscClient;
	private final TronProperties tronProperties;
	private final EthereumProperties ethereumProperties;
	private final BscProperties bscProperties;

	public PayoutBalancePreflightService(TronClient tronClient, EthereumClient ethereumClient,
			BscClient bscClient, TronProperties tronProperties, EthereumProperties ethereumProperties,
			BscProperties bscProperties) {
		this.tronClient = tronClient;
		this.ethereumClient = ethereumClient;
		this.bscClient = bscClient;
		this.tronProperties = tronProperties;
		this.ethereumProperties = ethereumProperties;
		this.bscProperties = bscProperties;
	}

	public void requireSufficient(List<WithdrawalRequest> withdrawals, String tronPrivateKey,
			String ethPrivateKey, String bepPrivateKey) {
		Map<String, BigDecimal> totals = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, String> addresses = new LinkedHashMap<>();
		for (WithdrawalRequest withdrawal : withdrawals) {
			String network = withdrawal.network();
			String privateKey = selectPrivateKey(network, tronPrivateKey, ethPrivateKey, bepPrivateKey);
			String address = addresses.computeIfAbsent(network,
					ignored -> deriveAddress(network, privateKey));
			String configuredAddress = configuredAddress(network);
			boolean addressMatches = "TRC20".equals(network)
					? configuredAddress == null || configuredAddress.equals(address)
					: configuredAddress == null || configuredAddress.equalsIgnoreCase(address);
			if (!addressMatches) {
				throw new WithdrawalException(409, "derived hot wallet does not match configured hot wallet");
			}
			totals.merge(network, withdrawal.usdtAmount(), BigDecimal::add);
			counts.merge(network, 1, Integer::sum);
		}

		for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
			String network = entry.getKey();
			String address = addresses.get(network);
			int count = counts.get(network);
			if ("TRC20".equals(network)) {
				checkAtLeast("TRC20 USDT", entry.getValue(), tronClient.getUsdtBalance(address));
				BigDecimal requiredTrx = BigDecimal.valueOf(tronProperties.getFeeLimit())
						.divide(TRX_PER_SUN).multiply(BigDecimal.valueOf(count));
				checkAtLeast("TRX", requiredTrx, tronClient.getTrxBalance(address));
			}
			else if ("ERC20".equals(network)) {
				checkAtLeast("ERC20 USDT", entry.getValue(), ethereumClient.getUsdtBalance(address));
				BigInteger gasPrice = ethereumClient.queryGasPrice();
				BigDecimal requiredEth = nativeFee(gasPrice, ethereumProperties.getGasLimit(), count);
				checkAtLeast("ETH", requiredEth, ethereumClient.getEthBalance(address));
			}
			else if ("BEP20".equals(network)) {
				checkAtLeast("BEP20 USDT", entry.getValue(), bscClient.getUsdtBalance(address));
				BigInteger gasPrice = bscClient.queryGasPrice();
				BigDecimal requiredBnb = nativeFee(gasPrice, bscProperties.getGasLimit(), count);
				checkAtLeast("BNB", requiredBnb, bscClient.getBnbBalance(address));
			}
			else {
				throw new WithdrawalException(400, "unsupported withdrawal network: " + network);
			}
		}
	}

	private BigDecimal nativeFee(BigInteger gasPrice, long gasLimit, int count) {
		return new BigDecimal(gasPrice.multiply(BigInteger.valueOf(gasLimit))
				.multiply(BigInteger.valueOf(count))).divide(NATIVE_DECIMALS, 18,
					java.math.RoundingMode.UP);
	}

	private void checkAtLeast(String asset, BigDecimal required, BigDecimal available) {
		if (available == null) {
			throw new WithdrawalException(409, asset + " 余额查询结果无效");
		}
		if (available.compareTo(required) < 0) {
			BigDecimal missing = required.subtract(available);
			throw new WithdrawalException(409, asset + " 余额不足：本次需要 "
					+ decimal(required) + "，当前余额 " + decimal(available)
					+ "，还差 " + decimal(missing));
		}
	}

	private String decimal(BigDecimal value) {
		return value == null ? "unknown" : value.stripTrailingZeros().toPlainString();
	}

	private String deriveAddress(String network, String privateKey) {
		if (privateKey == null || privateKey.isBlank()) {
			throw new WithdrawalException(400, "missing " + network + " hot wallet private key");
		}
		String normalized = PrivateKeyNormalizer.normalize(privateKey);
		return switch (network) {
			case "TRC20" -> tronClient.addressFromPrivateKey(normalized);
			case "ERC20" -> ethereumClient.addressFromPrivateKey(normalized);
			case "BEP20" -> bscClient.addressFromPrivateKey(normalized);
			default -> throw new WithdrawalException(400, "unsupported withdrawal network: " + network);
		};
	}

	private String selectPrivateKey(String network, String tronPrivateKey, String ethPrivateKey,
			String bepPrivateKey) {
		return switch (network) {
			case "TRC20" -> tronPrivateKey;
			case "ERC20" -> ethPrivateKey;
			case "BEP20" -> bepPrivateKey;
			default -> throw new WithdrawalException(400, "unsupported withdrawal network: " + network);
		};
	}

	private String configuredAddress(String network) {
		return switch (network) {
			case "TRC20" -> blankToNull(tronProperties.getHotWalletAddress());
			case "ERC20" -> blankToNull(ethereumProperties.getHotWalletAddress());
			case "BEP20" -> blankToNull(bscProperties.getHotWalletAddress());
			default -> null;
		};
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
