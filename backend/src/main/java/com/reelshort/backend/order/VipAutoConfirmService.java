package com.reelshort.backend.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		// Expire overdue orders first, then get remaining pending
		vipOrderService.expireOverdueOrders();
		List<VipOrder> pendingOrders = vipOrderService.pendingOrders();
		if (pendingOrders.isEmpty()) {
			return;
		}
		try {
			List<TronClient.IncomingTransfer> transfers = tronClient.fetchIncomingUsdtTransfers(collectionAddress, POLL_LIMIT);
			// H4: 金额可能重复（多个订单 suffix 相同或恰好金额一致），用 List 避免静默丢弃。
			// 按订单创建时间排序，先创建的先匹配。
			Map<String, List<VipOrder>> amountToOrders = new HashMap<>();
			for (VipOrder order : pendingOrders) {
				String key = order.payableAmount().stripTrailingZeros().toPlainString();
				amountToOrders.computeIfAbsent(key, k -> new ArrayList<>()).add(order);
			}
			for (TronClient.IncomingTransfer transfer : transfers) {
				String amountKey = transfer.amount().stripTrailingZeros().toPlainString();
				List<VipOrder> candidates = amountToOrders.get(amountKey);
				if (candidates == null || candidates.isEmpty()) {
					continue;
				}
				// 取最早创建的候选订单匹配（FIFO）
				VipOrder matched = candidates.remove(0);
				if (matched.txHash() == null) {
					log.info("Auto-confirming VIP order {} with tx {} amount {}", matched.orderNo(), transfer.txHash(), amountKey);
					vipOrderService.autoConfirm(matched.id(), transfer.txHash());
				}
			}
		}
		catch (Exception exception) {
			log.warn("VIP auto-confirm polling failed: {}", exception.getMessage());
		}
	}
}
