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
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import jakarta.annotation.PreDestroy;

@Service
public class EthereumClient {

	private static final Logger log = LoggerFactory.getLogger(EthereumClient.class);
	private static final BigDecimal USDT_DECIMALS = new BigDecimal("1000000");
	private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(20_000_000_000L);
	private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

	private final EthereumProperties properties;
	private final Web3j web3j;

	public EthereumClient(EthereumProperties properties) {
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
			return new BigDecimal(Numeric.toBigInt(hexValue)).divide(USDT_DECIMALS, 6, RoundingMode.DOWN);
		}
		catch (WithdrawalException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "USDT balance query failed: " + exception.getMessage());
		}
	}

	public BigDecimal getEthBalance(String address) {
		try {
			BigInteger wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
			return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
		}
		catch (Exception exception) {
			throw new WithdrawalException(503, "ETH balance query failed: " + exception.getMessage());
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
		Function transferFunction = new Function("transfer",
				List.of(new Address(toAddress), new Uint256(usdtAmount.multiply(USDT_DECIMALS).toBigIntegerExact())),
				List.of(new TypeReference<Bool>() {}));
		var rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
				nonce, gasPrice, BigInteger.valueOf(properties.getGasLimit()), properties.getUsdtContract(),
				BigInteger.ZERO, FunctionEncoder.encode(transferFunction));
		byte[] signedBytes = org.web3j.crypto.TransactionEncoder.signMessage(
				rawTransaction, properties.getChainId(), credentials);
		String signedRaw = Numeric.toHexString(signedBytes);
		String txHash = Numeric.toHexString(Hash.sha3(signedBytes));
		return new PreparedPayoutTransaction("ERC20", credentials.getAddress(), properties.getUsdtContract(),
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
		return queryTransactionStatus(txHash, null);
	}

	public PayoutChainStatus queryTransactionStatus(String txHash, BigInteger fallbackGasPrice) {
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
			String effectiveGasPrice = receipt.getEffectiveGasPrice();
			BigInteger gasPrice = effectiveGasPrice == null || effectiveGasPrice.isBlank()
					? fallbackGasPrice : Numeric.decodeQuantity(effectiveGasPrice);
			BigDecimal actualFee = gasPrice == null || receipt.getGasUsed() == null ? null
					: new BigDecimal(receipt.getGasUsed().multiply(gasPrice))
							.divide(BigDecimal.TEN.pow(18), 18, RoundingMode.DOWN);
			return PayoutChainStatus.confirmed(confirmations, actualFee, actualFee == null ? null : "ETH");
		}
		catch (Exception exception) {
			return PayoutChainStatus.unknown(exception.getMessage());
		}
	}

	@Deprecated
	public String transferUSDT(String hexPrivateKey, String toAddress, BigDecimal usdtAmount) {
		String address = addressFromPrivateKey(hexPrivateKey);
		PreparedPayoutTransaction prepared = signTransfer(
				hexPrivateKey, toAddress, usdtAmount, queryPendingNonce(address));
		PayoutBroadcastResult result = broadcastSignedTransaction(prepared.signedRawTransaction(), prepared.txHash());
		if (result.disposition() != PayoutBroadcastDisposition.ACCEPTED) {
			throw new WithdrawalException(502, "ERC-20 broadcast failed: " + result.detail());
		}
		return prepared.txHash();
	}

	@PreDestroy
	public void shutdown() {
		web3j.shutdown();
	}
}
