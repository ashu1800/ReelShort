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

	private String hotWalletAddress = "";

	private int incomingTransferMaxPages = 50;

	/** Maximum bytes accepted from a TronGrid response. */
	private int maxResponseBytes = 5 * 1024 * 1024;

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
		if (requiredConfirmations < 1 || requiredConfirmations > 100_000) {
			throw new IllegalArgumentException("reelshort.tron.required-confirmations must be between 1 and 100000");
		}
		this.requiredConfirmations = requiredConfirmations;
	}

	public String getHotWalletAddress() {
		return hotWalletAddress;
	}

	public void setHotWalletAddress(String hotWalletAddress) {
		this.hotWalletAddress = hotWalletAddress;
	}

	public int getIncomingTransferMaxPages() {
		return incomingTransferMaxPages;
	}

	public void setIncomingTransferMaxPages(int incomingTransferMaxPages) {
		if (incomingTransferMaxPages < 1 || incomingTransferMaxPages > 100) {
			throw new IllegalArgumentException("reelshort.tron.incoming-transfer-max-pages must be between 1 and 100");
		}
		this.incomingTransferMaxPages = incomingTransferMaxPages;
	}

	public int getMaxResponseBytes() {
		return maxResponseBytes;
	}

	public void setMaxResponseBytes(int maxResponseBytes) {
		if (maxResponseBytes < 1 || maxResponseBytes > 50 * 1024 * 1024) {
			throw new IllegalArgumentException("reelshort.tron.max-response-bytes is out of range");
		}
		this.maxResponseBytes = maxResponseBytes;
	}
}
