package com.reelshort.backend.order;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.reelshort.backend.withdrawal.TronClient;
import com.reelshort.backend.withdrawal.TronProperties;

@Service
public class VipAutoConfirmService {

	private static final Logger log = LoggerFactory.getLogger(VipAutoConfirmService.class);
	private static final int POLL_LIMIT = 200;

	private final VipOrderService vipOrderService;
	private final TronClient tronClient;
	private final TronProperties tronProperties;

	public VipAutoConfirmService(VipOrderService vipOrderService, TronClient tronClient, TronProperties tronProperties) {
		this.vipOrderService = vipOrderService;
		this.tronClient = tronClient;
		this.tronProperties = tronProperties;
	}

	@Scheduled(fixedDelayString = "${reelshort.vip.auto-confirm-interval:120000}", initialDelay = 30_000)
	public void autoConfirmPendingOrders() {
		vipOrderService.expireOverdueOrders();
		List<VipOrder> pending = vipOrderService.pendingOrders();
		if (pending.isEmpty()) {
			return;
		}
		Map<String, List<VipOrder>> ordersByAddress = pending.stream()
				.filter(order -> order.receivingWalletAddress() != null && !order.receivingWalletAddress().isBlank())
				.collect(Collectors.groupingBy(VipOrder::receivingWalletAddress));
		for (Map.Entry<String, List<VipOrder>> entry : ordersByAddress.entrySet()) {
			pollAddress(entry.getKey(), entry.getValue());
		}
	}

	private void pollAddress(String address, List<VipOrder> orders) {
		try {
			OffsetDateTime earliest = orders.stream().map(VipOrder::createdAt).min(OffsetDateTime::compareTo).orElseThrow();
			for (TronClient.IncomingTransfer transfer : tronClient.fetchIncomingUsdtTransfers(address, POLL_LIMIT,
					earliest)) {
				VipOrder matched = orders.stream()
						.filter(order -> VipPaymentMatcher.matches(order, transfer, 0))
						.findFirst()
						.orElse(null);
				if (matched == null) {
					continue;
				}
				try {
					TronClient.IncomingTransfer verified = tronClient.verifyIncomingTransfer(transfer);
					if (!VipPaymentMatcher.matches(matched, verified, tronProperties.getRequiredConfirmations())) {
						continue;
					}
					vipOrderService.autoConfirm(matched.id(), verified);
					log.info("Auto-confirmed VIP order {} with transaction {}", matched.orderNo(), transfer.txHash());
				}
				catch (RuntimeException exception) {
					log.warn("VIP auto-confirm rejected order {}: {}", matched.orderNo(), exception.getMessage());
				}
			}
		}
		catch (RuntimeException exception) {
			log.warn("VIP auto-confirm polling failed for configured address: {}", exception.getMessage());
		}
	}
}
