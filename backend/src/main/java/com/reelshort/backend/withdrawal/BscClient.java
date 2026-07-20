package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import jakarta.annotation.PreDestroy;

/**
 * 币安链（BSC / BEP-20）USDT 打款客户端。
 * <p>
 * 与 {@link EthereumClient} 同属 EVM 栈（web3j、nonce、eth_sendRawTransaction），但有三个硬差异：
 * <ul>
 *   <li>USDT decimals 为 18（以太坊/波场为 6），由 {@link BscProperties#getUsdtDecimals()} 提供换算因子；</li>
 *   <li>network 标识为 "BEP20"，写入 {@link PreparedPayoutTransaction} 区分 ERC20；</li>
 *   <li>chainId 为 56，USDT 合约地址不同。</li>
 * </ul>
 * 因 decimals 与 network 差异会直接影响打款金额正确性与持久化 intent 一致性，故独立为单独 bean，不复用 EthereumClient。
 */
@Service
public class BscClient {

	private static final Logger log = LoggerFactory.getLogger(BscClient.class);
	private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(5_000_000_000L); // 5 gwei，BSC 常见下限
	private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

	private final BscProperties properties;
	private final Web3j web3j;

	public BscClient(BscProperties properties) {
		this.properties = properties;
		this.web3j = Web3j.build(new HttpService(properties.getRpcUrl()));
	}

	public String addressFromPrivateKey(String hexPrivateKey) {
		try {
			return Credentials.create(hexPrivateKey).getAddress();
		}
		catch (Exception exception) {
			throw new WithdrawalException(400, "invalid hot wallet private key: " + exception.getMessage());
		}
	}

	public BigDecimal getUsdtBalance(String address) {
		try {
			Function function = new Function("balanceOf", List.of(new Address(address)),
					List.of(new TypeReference<Uint256>() {}));
			EthCall response = web3j.ethCall(Transaction.createEthCallTransaction(
					ZERO_ADDRESS, properties.getUsdtContract(), FunctionEncoder.encode(function)),
					DefaultBlockParameterName.LATEST).send();
			if (response.hasError()) {
				throw new WithdrawalException(503, "USDT balance query failed: " + response.getError().getMessage());
			}
			String hexValue = response.getValue();
			if (hexValue == null || hexValue.length() < 10) {
				return BigDecimal.ZERO;
			}
			return new BigDecimal(Numeric.toBigInt(hexValue))
					.divide(properties.decimalFactor(), properties.getUsdtDecimals(), RoundingMode.DOWN);
		}
		catch (WithdrawalException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "USDT balance query failed: " + exception.getMessage());
		}
	}

	public BigInteger queryPendingNonce(String fromAddress) {
		try {
			var response = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).send();
			if (response.hasError()) {
				throw new WithdrawalException(503, "nonce query failed: " + response.getError().getMessage());
			}
			if (response.getTransactionCount() == null) {
				throw new WithdrawalException(503, "nonce query failed: node returned no nonce");
			}
			return response.getTransactionCount();
		}
		catch (WithdrawalException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "nonce query failed: " + exception.getMessage());
		}
	}

	public PreparedPayoutTransaction signTransfer(String hexPrivateKey, String toAddress, BigDecimal usdtAmount,
			BigInteger nonce) {
		return signTransfer(hexPrivateKey, toAddress, usdtAmount, nonce, queryGasPrice());
	}

	public PreparedPayoutTransaction signTransfer(String hexPrivateKey, String toAddress, BigDecimal usdtAmount,
			BigInteger nonce, BigInteger gasPrice) {
		Credentials credentials;
		try {
			credentials = Credentials.create(hexPrivateKey);
		}
		catch (Exception exception) {
			throw new WithdrawalException(400, "invalid hot wallet private key: " + exception.getMessage());
		}
		// 金额按 BSC USDT 的 18 decimals 换算（与以太坊的 6 位不同）。
		Function transferFunction = new Function("transfer",
				List.of(new Address(toAddress),
						new Uint256(usdtAmount.multiply(properties.decimalFactor()).toBigIntegerExact())),
				List.of(new TypeReference<Bool>() {}));
		var rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
				nonce, gasPrice, BigInteger.valueOf(properties.getGasLimit()), properties.getUsdtContract(),
				BigInteger.ZERO, FunctionEncoder.encode(transferFunction));
		byte[] signedBytes = org.web3j.crypto.TransactionEncoder.signMessage(
				rawTransaction, properties.getChainId(), credentials);
		String signedRaw = Numeric.toHexString(signedBytes);
		String txHash = Numeric.toHexString(Hash.sha3(signedBytes));
		return new PreparedPayoutTransaction("BEP20", credentials.getAddress(), properties.getUsdtContract(),
				properties.getChainId(), nonce, signedRaw, txHash);
	}

	public BigInteger queryGasPrice() {
		try {
			var response = web3j.ethGasPrice().send();
			if (!response.hasError() && response.getGasPrice() != null) {
				return response.getGasPrice();
			}
		}
		catch (Exception exception) {
			log.warn("Failed to query gas price, using fallback: {}", exception.getMessage());
		}
		return DEFAULT_GAS_PRICE;
	}

	public PayoutBroadcastResult broadcastSignedTransaction(String signedRawTransaction, String expectedTxHash) {
		try {
			var response = web3j.ethSendRawTransaction(signedRawTransaction).send();
			if (!response.hasError()) {
				String returnedHash = response.getTransactionHash();
				if (returnedHash == null || !returnedHash.equalsIgnoreCase(expectedTxHash)) {
					return PayoutBroadcastResult.unknown("node returned an unexpected transaction hash");
				}
				return PayoutBroadcastResult.accepted();
			}
			String message = response.getError().getMessage();
			String normalized = message == null ? "" : message.toLowerCase();
			if (normalized.contains("already known") || normalized.contains("known transaction")) {
				return PayoutBroadcastResult.accepted();
			}
			if (normalized.contains("insufficient funds") || normalized.contains("invalid sender")
					|| normalized.contains("intrinsic gas too low")) {
				return PayoutBroadcastResult.rejected(message);
			}
			return PayoutBroadcastResult.unknown(message);
		}
		catch (Exception exception) {
			return PayoutBroadcastResult.unknown(exception.getMessage());
		}
	}

	public PayoutChainStatus queryTransactionStatus(String txHash) {
		try {
			var response = web3j.ethGetTransactionReceipt(txHash).send();
			if (response.hasError()) {
				return PayoutChainStatus.unknown(response.getError().getMessage());
			}
			if (response.getTransactionReceipt().isEmpty()) {
				return PayoutChainStatus.of(PayoutChainState.NOT_FOUND, 0);
			}
			var receipt = response.getTransactionReceipt().orElseThrow();
			if (!receipt.isStatusOK()) {
				return PayoutChainStatus.of(PayoutChainState.FAILED, 0);
			}
			BigInteger latest = web3j.ethBlockNumber().send().getBlockNumber();
			int confirmations = latest.subtract(receipt.getBlockNumber()).add(BigInteger.ONE)
					.max(BigInteger.ZERO).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
			return PayoutChainStatus.of(PayoutChainState.CONFIRMED, confirmations);
		}
		catch (Exception exception) {
			return PayoutChainStatus.unknown(exception.getMessage());
		}
	}

	@PreDestroy
	public void shutdown() {
		web3j.shutdown();
	}
}
