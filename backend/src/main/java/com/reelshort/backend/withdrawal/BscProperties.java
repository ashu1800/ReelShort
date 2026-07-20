package com.reelshort.backend.withdrawal;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 币安链（BSC / BEP-20）USDT 提现打款配置。
 * <p>
 * 热钱包私钥不在此配置——由管理员在每次批量打款时提供，仅在内存中用于签名。
 * <p>
 * 注意：BSC 上的 USDT 合约使用 18 位 decimals（与以太坊/波场的 6 位不同），打款金额换算必须以此为准，
 * 否则金额会偏差 10^12 倍。
 */
@ConfigurationProperties(prefix = "reelshort.bsc")
public class BscProperties {

	private String nodeUrl = "https://bsc-dataseed.binance.org/";

	/** RPC API key（部分私有/付费节点需要，追加到 nodeUrl 末尾）。 */
	private String apiKey = "";

	/** USDT BEP-20 合约地址（BSC 主网，18 decimals）。 */
	private String usdtContract = "0x55d398326f99059fF775485246999027B3197955";

	/** BSC 主网 chainId。 */
	private long chainId = 56;

	/** BEP-20 transfer 默认 gas limit。 */
	private long gasLimit = 100_000L;

	private int requiredConfirmations = 15;

	/** BSC 上 USDT 的小数位数（18，与以太坊/波场的 6 位不同）。 */
	private int usdtDecimals = 18;

	private String hotWalletAddress = "";

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

	public int getUsdtDecimals() {
		return usdtDecimals;
	}

	public void setUsdtDecimals(int usdtDecimals) {
		this.usdtDecimals = usdtDecimals;
	}

	/** 按 usdtDecimals 计算 10 的幂（用于链上最小单位换算）。 */
	public BigDecimal decimalFactor() {
		return new BigDecimal("1" + "0".repeat(Math.max(0, usdtDecimals)));
	}

	public String getHotWalletAddress() {
		return hotWalletAddress;
	}

	public void setHotWalletAddress(String hotWalletAddress) {
		this.hotWalletAddress = hotWalletAddress;
	}
}
