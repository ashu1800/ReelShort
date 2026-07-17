package com.reelshort.backend.withdrawal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tron / TRC20 configuration. The hot wallet private key is NOT stored here — it is provided by the
 * admin on each batch payout request and used only in-memory for signing.
 */
@ConfigurationProperties(prefix = "reelshort.tron")
public class TronProperties {

	private String nodeUrl = "https://api.trongrid.io";

	private String apiKey = "";

	/** USDT TRC20 contract on Tron mainnet. */
	private String usdtContract = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

	/** Max TRX fee (sun) per transaction. 100 TRX = 100_000_000 sun. */
	private long feeLimit = 100_000_000L;

	private int requiredConfirmations = 20;

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

	public String getUsdtContract() {
		return usdtContract;
	}

	public void setUsdtContract(String usdtContract) {
		this.usdtContract = usdtContract;
	}

	public long getFeeLimit() {
		return feeLimit;
	}

	public void setFeeLimit(long feeLimit) {
		this.feeLimit = feeLimit;
	}

	public int getRequiredConfirmations() {
		return requiredConfirmations;
	}

	public void setRequiredConfirmations(int requiredConfirmations) {
		this.requiredConfirmations = requiredConfirmations;
	}
}
