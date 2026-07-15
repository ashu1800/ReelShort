package com.reelshort.backend.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.wallet.WalletService;
import com.reelshort.backend.withdrawal.TronClient;

/**
 * Scheduled task that polls TronGrid for incoming USDT transfers to the collection address and
 * auto-confirms VIP orders whose unique payable amount matches an on-chain transaction.
 */
@Service
public class VipAutoConfirmService {

	private static final Logger log = LoggerFactory.getLogger(VipAutoConfirmService.class);
	private static final int POLL_LIMIT = 50;

	private final VipOrderService vipOrderService;
	private final TronClient tronClient;
	private final SystemConfigService systemConfigService;

	public VipAutoConfirmService(VipOrderService vipOrderService, TronClient tronClient,
			SystemConfigService systemConfigService) {
		this.vipOrderService = vipOrderService;
		this.tronClient = tronClient;
		this.systemConfigService = systemConfigService;
	}

	@Scheduled(fixedDelayString = "${reelshort.vip.auto-confirm-interval:120000}", initialDelay = 30_000)
	public void autoConfirmPendingOrders() {
		String collectionAddress;
		try {
			collectionAddress = systemConfigService.stringValue(SystemConfigRegistry.VIP_COLLECTION_ADDRESS);
		}
		catch (Exception exception) {
			return;
		}
		if (collectionAddress == null || collectionAddress.isBlank()) {
			return;
		}
		List<VipOrder> pendingOrders = vipOrderService.pendingOrders();
		if (pendingOrders.isEmpty()) {
			return;
		}
		try {
			List<TronClient.IncomingTransfer> transfers = tronClient.fetchIncomingUsdtTransfers(collectionAddress, POLL_LIMIT);
			// Build a map of payable amount → order for fast lookup
			Map<String, VipOrder> amountToOrder = pendingOrders.stream()
					.collect(Collectors.toMap(
							o -> o.payableAmount().stripTrailingZeros().toPlainString(),
							o -> o,
							(a, b) -> a));
			for (TronClient.IncomingTransfer transfer : transfers) {
				String amountKey = transfer.amount().stripTrailingZeros().toPlainString();
				VipOrder matched = amountToOrder.get(amountKey);
				if (matched != null && matched.txHash() == null) {
					log.info("Auto-confirming VIP order {} with tx {} amount {}", matched.orderNo(), transfer.txHash(), amountKey);
					vipOrderService.autoConfirm(matched.id(), transfer.txHash());
					amountToOrder.remove(amountKey);
				}
			}
		}
		catch (Exception exception) {
			log.warn("VIP auto-confirm polling failed: {}", exception.getMessage());
		}
	}
}
