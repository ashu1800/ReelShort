package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
	private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";
	private static final int NONCE_RETRY_MAX = 3;

	private final EthereumProperties properties;
	private final Web3j web3j;
	// H1: 进程内 nonce 计数器，按热钱包地址隔离，防止批量打款时 nonce 冲突
	private final Map<String, AtomicLong> nonceCounters = new ConcurrentHashMap<>();

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
	 * M2: 查询失败时抛异常（与 TronClient 行为一致），而非静默返回 0。
	 */
	public BigDecimal getUsdtBalance(String address) {
		try {
			Function function = new Function("balanceOf",
					List.of(new Address(address)),
					List.of(new TypeReference<Uint256>() {}));
			String encodedFunction = FunctionEncoder.encode(function);
			// M2: 用零地址作 from 更稳妥（view 函数不依赖 from）
			EthCall response = web3j.ethCall(
					Transaction.createEthCallTransaction(ZERO_ADDRESS, properties.getUsdtContract(), encodedFunction),
					DefaultBlockParameterName.LATEST).send();
			if (response.hasError()) {
				throw new WithdrawalException(503, "USDT balance query failed: " + response.getError().getMessage());
			}
			String hexValue = response.getValue();
			if (hexValue == null || hexValue.length() < 10) {
				return BigDecimal.ZERO;
			}
			BigInteger raw = Numeric.toBigInt(hexValue);
			return new BigDecimal(raw).divide(USDT_DECIMALS, 6, RoundingMode.DOWN);
		}
		catch (WithdrawalException exception) {
			throw exception;
		}
		catch (Exception exception) {
			log.warn("Failed to query USDT balance for {}: {}", address, exception.getMessage());
			throw new WithdrawalException(503, "USDT balance query failed: " + exception.getMessage());
		}
	}

	/**
	 * 查询地址的 ETH 余额（用于 gas 费估算）。
	 * M2: 查询失败时抛异常。
	 */
	public BigDecimal getEthBalance(String address) {
		try {
			BigInteger wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
			return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
		}
		catch (Exception exception) {
			log.warn("Failed to query ETH balance for {}: {}", address, exception.getMessage());
			throw new WithdrawalException(503, "ETH balance query failed: " + exception.getMessage());
		}
	}

	/**
	 * H1: 获取并递增 nonce。进程内维护计数器，避免批量打款时 RPC 最终一致性延迟导致 nonce 冲突。
	 * 首次从链上 PENDING 查询初始化，后续递增。若收到 "nonce too low" 错误，重新同步后重试。
	 */
	private synchronized BigInteger getAndIncrementNonce(String fromAddress) {
		AtomicLong counter = nonceCounters.computeIfAbsent(fromAddress, k -> {
			try {
				long chainNonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING)
						.send().getTransactionCount().longValue();
				return new AtomicLong(chainNonce);
			}
			catch (Exception exception) {
				log.warn("Failed to query nonce for {}, defaulting to 0: {}", fromAddress, exception.getMessage());
				return new AtomicLong(0);
			}
		});
		return BigInteger.valueOf(counter.getAndIncrement());
	}

	/**
	 * H1: nonce 冲突时重新同步链上最新值并重试。
	 */
	private synchronized void resyncNonce(String fromAddress) {
		try {
			long chainNonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING)
					.send().getTransactionCount().longValue();
			AtomicLong counter = nonceCounters.get(fromAddress);
			if (counter != null) {
				counter.set(chainNonce);
			}
		}
		catch (Exception exception) {
			log.warn("Failed to resync nonce for {}: {}", fromAddress, exception.getMessage());
		}
	}

	/**
	 * 转账 ERC-20 USDT 到目标地址，返回交易哈希。
	 * 私钥仅在方法栈内用于签名，不持久化。
	 * H1: nonce 冲突时自动重试最多 NONCE_RETRY_MAX 次。
	 */
	public String transferUSDT(String hexPrivateKey, String toAddress, BigDecimal usdtAmount) {
		Credentials credentials = Credentials.create(hexPrivateKey);
		String fromAddress = credentials.getAddress();

		// 1. 编码 ERC-20 transfer(address,uint256) 调用数据
		BigInteger rawAmount = usdtAmount.multiply(USDT_DECIMALS).toBigInteger();
		// M5: ERC-20 transfer 返回类型是 bool，不是 Uint8
		Function transferFunction = new Function("transfer",
				List.of(new Address(toAddress), new Uint256(rawAmount)),
				List.of(new TypeReference<Bool>() {}));
		String data = FunctionEncoder.encode(transferFunction);

		BigInteger gasLimit = BigInteger.valueOf(properties.getGasLimit());

		// H1: nonce 冲突重试循环
		for (int attempt = 0; attempt < NONCE_RETRY_MAX; attempt++) {
			try {
				// 2. 获取 nonce（进程内计数器）
				BigInteger nonce = getAndIncrementNonce(fromAddress);

				// 3. 估算 gas price
				BigInteger gasPrice;
				try {
					gasPrice = web3j.ethGasPrice().send().getGasPrice();
				}
				catch (Exception exception) {
					// M4: 记录 warn 日志而非静默吞掉
					log.warn("Failed to estimate gas price, using default {}: {}",
							DEFAULT_GAS_PRICE, exception.getMessage());
					gasPrice = DEFAULT_GAS_PRICE;
				}

				// 4. 构造 legacy 原始交易（EIP-155 签名会附加 chainId）
				org.web3j.crypto.RawTransaction rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
						nonce, gasPrice, gasLimit, properties.getUsdtContract(), BigInteger.ZERO, data);

				// 5. 签名
				byte[] signedMessage = org.web3j.crypto.TransactionEncoder.signMessage(rawTransaction,
						properties.getChainId(), credentials);
				String hexValue = Numeric.toHexString(signedMessage);

				// 6. 广播
				var sendResult = web3j.ethSendRawTransaction(hexValue).send();
				if (sendResult.hasError()) {
					String errorMsg = sendResult.getError().getMessage();
					// H1: nonce 冲突时重新同步并重试
					if (errorMsg != null && (errorMsg.contains("nonce") || errorMsg.contains("replacement"))) {
						log.warn("Nonce conflict for {} (attempt {}), resyncing: {}", fromAddress, attempt + 1, errorMsg);
						resyncNonce(fromAddress);
						continue;
					}
					throw new WithdrawalException(502, "ERC-20 broadcast failed: " + errorMsg);
				}
				String txHash = sendResult.getTransactionHash();
				if (txHash == null || txHash.isBlank()) {
					throw new WithdrawalException(502, "ERC-20 broadcast failed: no transaction hash returned");
				}
				return txHash;
			}
			catch (WithdrawalException exception) {
				throw exception;
			}
			catch (Exception exception) {
				String msg = exception.getMessage();
				if (msg != null && (msg.contains("nonce") || msg.contains("replacement")) && attempt < NONCE_RETRY_MAX - 1) {
					log.warn("Nonce conflict for {} (attempt {}): {}", fromAddress, attempt + 1, msg);
					resyncNonce(fromAddress);
					continue;
				}
				log.error("ERC-20 USDT transfer to {} failed: {}", toAddress, msg);
				throw new WithdrawalException(502, "ERC-20 transfer failed: " + msg);
			}
		}
		throw new WithdrawalException(502, "ERC-20 transfer failed: nonce conflict after " + NONCE_RETRY_MAX + " retries");
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
