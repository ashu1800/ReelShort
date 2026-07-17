package com.reelshort.backend.withdrawal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 以太坊 ERC-20 USDT 提现打款配置。
 * <p>
 * 热钱包私钥不在此配置——由管理员在每次批量打款时提供，仅在内存中用于签名。
 */
@ConfigurationProperties(prefix = "reelshort.eth")
public class EthereumProperties {

	private String nodeUrl = "https://mainnet.infura.io/v3/";

	/** Infura API key（追加到 nodeUrl 末尾）。 */
	private String apiKey = "";

	/** USDT ERC-20 合约地址（以太坊主网）。 */
	private String usdtContract = "0xdAC17F958D2ee523a2206206994597C13D831ec7";

	/** 以太坊主网 chainId。 */
	private long chainId = 1;

	/** ERC-20 transfer 默认 gas limit（USDT transfer 实际约 60k）。 */
	private long gasLimit = 100_000L;

	private int requiredConfirmations = 12;

	public String getNodeUrl() {
		return nodeUrl;
	}

	public void setNodeUrl(String nodeUrl) {
		this.nodeUrl = nodeUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	/** 返回完整的 RPC endpoint（nodeUrl + apiKey）。 */
	public String getRpcUrl() {
		return nodeUrl.endsWith("/") ? nodeUrl + apiKey : nodeUrl + "/" + apiKey;
	}

	public String getUsdtContract() {
		return usdtContract;
	}

	public void setUsdtContract(String usdtContract) {
		this.usdtContract = usdtContract;
	}

	public long getChainId() {
		return chainId;
	}

	public void setChainId(long chainId) {
		this.chainId = chainId;
	}

	public long getGasLimit() {
		return gasLimit;
	}

	public void setGasLimit(long gasLimit) {
		this.gasLimit = gasLimit;
	}

	public int getRequiredConfirmations() {
		return requiredConfirmations;
	}

	public void setRequiredConfirmations(int requiredConfirmations) {
		this.requiredConfirmations = requiredConfirmations;
	}
}
