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
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import jakarta.annotation.PreDestroy;

/**
 * 以太坊 ERC-20 USDT 转账客户端，基于 Web3j。
 * 用于后台提现审批后的链上打款。热钱包私钥由管理员在请求中提供，仅内存使用，不持久化。
 * 不依赖生成的合约包装器，直接用 ABI Function 编码调用 ERC-20 方法。
 */
@Service
public class EthereumClient {

	private static final Logger log = LoggerFactory.getLogger(EthereumClient.class);
	private static final BigDecimal USDT_DECIMALS = new BigDecimal("1000000");
	private static final BigInteger DEFAULT_GAS_PRICE = BigInteger.valueOf(20_000_000_000L); // 20 Gwei

	private final EthereumProperties properties;
	private final Web3j web3j;

	public EthereumClient(EthereumProperties properties) {
		this.properties = properties;
		this.web3j = Web3j.build(new HttpService(properties.getRpcUrl()));
	}

	/**
	 * 从私钥派生以太坊地址（0x...）。
	 */
	public String addressFromPrivateKey(String hexPrivateKey) {
		try {
			return Credentials.create(hexPrivateKey).getAddress();
		}
		catch (Exception exception) {
			throw new WithdrawalException(400, "invalid hot wallet private key: " + exception.getMessage());
		}
	}

	/**
	 * 查询地址的 ERC-20 USDT 余额（通过 eth_call 调用 balanceOf）。
	 */
	public BigDecimal getUsdtBalance(String address) {
		try {
			Function function = new Function("balanceOf",
					List.of(new Address(address)),
					List.of(new TypeReference<Uint256>() {}));
			String encodedFunction = FunctionEncoder.encode(function);
			EthCall response = web3j.ethCall(
					Transaction.createEthCallTransaction(address, properties.getUsdtContract(), encodedFunction),
					DefaultBlockParameterName.LATEST).send();
			String hexValue = response.getValue();
			if (hexValue == null || hexValue.length() < 10) {
				return BigDecimal.ZERO;
			}
			BigInteger raw = Numeric.toBigInt(hexValue);
			return new BigDecimal(raw).divide(USDT_DECIMALS, 6, RoundingMode.DOWN);
		}
		catch (Exception exception) {
			log.warn("Failed to query USDT balance for {}: {}", address, exception.getMessage());
			return BigDecimal.ZERO;
		}
	}

	/**
	 * 查询地址的 ETH 余额（用于 gas 费估算）。
	 */
	public BigDecimal getEthBalance(String address) {
		try {
			BigInteger wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
			return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
		}
		catch (Exception exception) {
			log.warn("Failed to query ETH balance for {}: {}", address, exception.getMessage());
			return BigDecimal.ZERO;
		}
	}

	/**
	 * 转账 ERC-20 USDT 到目标地址，返回交易哈希。
	 * 私钥仅在方法栈内用于签名，不持久化。
	 * 使用原始交易构造+手动签名+eth_sendRawTransaction 广播。
	 */
	public String transferUSDT(String hexPrivateKey, String toAddress, BigDecimal usdtAmount) {
		try {
			Credentials credentials = Credentials.create(hexPrivateKey);
			String fromAddress = credentials.getAddress();

			// 1. 编码 ERC-20 transfer(address,uint256) 调用数据
			BigInteger rawAmount = usdtAmount.multiply(USDT_DECIMALS).toBigInteger();
			Function transferFunction = new Function("transfer",
					List.of(new Address(toAddress), new Uint256(rawAmount)),
					List.of(new TypeReference<Uint8>() {}));
			String data = FunctionEncoder.encode(transferFunction);

			// 2. 获取 nonce
			BigInteger nonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).send()
					.getTransactionCount();

			// 3. 估算 gas price（使用 chainId 估算或回退默认值）
			BigInteger gasPrice;
			try {
				gasPrice = web3j.ethGasPrice().send().getGasPrice();
			}
			catch (Exception ignored) {
				gasPrice = DEFAULT_GAS_PRICE;
			}

			// 4. 构造 legacy 原始交易（EIP-155 签名会附加 chainId）
			BigInteger gasLimit = BigInteger.valueOf(properties.getGasLimit());
			org.web3j.crypto.RawTransaction rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
					nonce, gasPrice, gasLimit, properties.getUsdtContract(), BigInteger.ZERO, data);

			// 5. 签名
			byte[] signedMessage = org.web3j.crypto.TransactionEncoder.signMessage(rawTransaction,
					properties.getChainId(), credentials);
			String hexValue = Numeric.toHexString(signedMessage);

			// 6. 广播
			String txHash = web3j.ethSendRawTransaction(hexValue).send().getTransactionHash();
			if (txHash == null || txHash.isBlank()) {
				throw new WithdrawalException(502, "ERC-20 broadcast failed: no transaction hash returned");
			}
			return txHash;
		}
		catch (WithdrawalException exception) {
			throw exception;
		}
		catch (Exception exception) {
			log.error("ERC-20 USDT transfer to {} failed: {}", toAddress, exception.getMessage());
			throw new WithdrawalException(502, "ERC-20 transfer failed: " + exception.getMessage());
		}
	}

	@PreDestroy
	public void shutdown() {
		if (web3j != null) {
			try {
				web3j.shutdown();
			}
			catch (Exception ignored) {
			}
		}
	}
}
