package com.reelshort.backend.order;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.reelshort.backend.withdrawal.TronClient;
import com.reelshort.backend.withdrawal.TronProperties;
import com.reelshort.backend.admin.AdminException;

@Service
public class VipAutoConfirmService {
	private static final Logger log = LoggerFactory.getLogger(VipAutoConfirmService.class);
	private static final int POLL_LIMIT = 200;

	private final VipOrderService vipOrderService;
	private final TronClient tronClient;
	private final TronProperties tronProperties;
	private final VipTransferScanCursorService cursorService;

	public VipAutoConfirmService(VipOrderService vipOrderService, TronClient tronClient,
			TronProperties tronProperties, VipTransferScanCursorService cursorService) {
		this.vipOrderService = vipOrderService;
		this.tronClient = tronClient;
		this.tronProperties = tronProperties;
		this.cursorService = cursorService;
	}

	@Scheduled(fixedDelayString = "${reelshort.vip.auto-confirm-interval:120000}", initialDelay = 30_000)
	public void autoConfirmPendingOrders() {
		vipOrderService.expireOverdueOrders();
		List<VipOrder> pending = vipOrderService.pendingOrders();
		Map<ReceiptSnapshot, List<VipOrder>> groups = pending.stream()
				.filter(order -> order.receivingWalletAddress() != null && order.tokenContractAddress() != null)
				.collect(Collectors.groupingBy(order -> new ReceiptSnapshot(
						order.receivingWalletAddress(), order.tokenContractAddress())));
		groups.forEach(this::pollSnapshot);
	}

	private void pollSnapshot(ReceiptSnapshot snapshot, List<VipOrder> orders) {
		OffsetDateTime earliest = orders.stream().map(VipOrder::createdAt)
				.min(OffsetDateTime::compareTo).orElseThrow();
		VipTransferScanCursorService.State cursor = cursorService.start(
				snapshot.address(), snapshot.contract(), earliest);
		String fingerprint = cursor.fingerprint();
		int pages = 0;
		try {
			do {
				TronClient.IncomingTransferPage page = tronClient.fetchIncomingUsdtTransferPage(
						snapshot.address(), snapshot.contract(), POLL_LIMIT, fingerprint);
				boolean deferred = false;
				for (TronClient.IncomingTransfer transfer : page.transfers()) {
					deferred |= processCandidate(orders, transfer) == CandidateOutcome.DEFER;
				}
				if (deferred) {
					return;
				}
				pages++;
				boolean reachedWindow = page.transfers().stream()
						.map(TronClient.IncomingTransfer::blockTimestamp)
						.filter(java.util.Objects::nonNull)
						.anyMatch(timestamp -> timestamp.isBefore(earliest));
				fingerprint = page.nextFingerprint();
				if (reachedWindow || fingerprint == null || fingerprint.isBlank()) {
					cursorService.complete(cursor.id());
					return;
				}
				cursorService.advance(cursor.id(), fingerprint);
			} while (pages < tronProperties.getIncomingTransferMaxPages());
			log.warn("VIP transfer scan paused at configured page limit for address {} and contract {}",
					snapshot.address(), snapshot.contract());
		}
		catch (RuntimeException exception) {
			log.warn("VIP transfer scan failed for receipt snapshot: {}", exception.getMessage());
		}
	}

	private CandidateOutcome processCandidate(List<VipOrder> orders, TronClient.IncomingTransfer transfer) {
		VipOrder matched = orders.stream()
				.filter(order -> VipPaymentMatcher.matches(order, transfer, 0))
				.findFirst().orElse(null);
		if (matched == null) {
			return CandidateOutcome.IRRELEVANT;
		}
		TronClient.IncomingTransfer verified = tronClient.verifyIncomingTransfer(transfer);
		if (!VipPaymentMatcher.matches(matched, verified, tronProperties.getRequiredConfirmations())) {
			return CandidateOutcome.DEFER;
		}
		try {
			vipOrderService.autoConfirm(matched.id(), verified);
		}
		catch (AdminException businessRejection) {
			log.warn("VIP transfer rejected for order {}: {}", matched.orderNo(), businessRejection.getMessage());
		}
		return CandidateOutcome.PROCESSED;
	}

	private enum CandidateOutcome {
		PROCESSED,
		IRRELEVANT,
		DEFER
	}

	private record ReceiptSnapshot(String address, String contract) {
	}
}
