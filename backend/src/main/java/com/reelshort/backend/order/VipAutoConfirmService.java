package com.reelshort.backend.order;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
		// PENDING 先于 EXPIRED，同一个金额如果同时能匹配两类订单，优先确认仍可支付的订单。
		List<VipOrder> scannable = new ArrayList<>(vipOrderService.pendingOrders());
		scannable.addAll(vipOrderService.recoverableExpiredOrders());
		Map<ReceiptSnapshot, List<VipOrder>> groups = scannable.stream()
				.filter(order -> order.receivingWalletAddress() != null && order.tokenContractAddress() != null)
				.collect(Collectors.groupingBy(order -> new ReceiptSnapshot(
						order.receivingWalletAddress(), order.tokenContractAddress())));
		groups.forEach(this::pollSnapshot);
	}

	/** 付款容忍窗口；单元测试里的 mock 未打桩时回退为 0。 */
	private Duration acceptanceGrace() {
		Duration configured = vipOrderService.confirmationGrace();
		return configured == null ? Duration.ZERO : configured;
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
		Duration grace = acceptanceGrace();
		List<VipOrder> pendingMatches = orders.stream()
				.filter(order -> "PENDING".equals(order.status()))
				.filter(order -> VipPaymentMatcher.matches(order, transfer, 0, grace))
				.toList();
		List<VipOrder> expiredMatches = pendingMatches.isEmpty()
				? orders.stream()
						.filter(order -> VipPaymentMatcher.matchesLatePayment(order, transfer, 0, grace))
						.toList()
				: List.of();
		boolean recovering = pendingMatches.isEmpty();
		VipOrder matched = pendingMatches.isEmpty()
				? (expiredMatches.isEmpty() ? null : expiredMatches.get(0))
				: pendingMatches.get(0);
		if (matched == null) {
			return CandidateOutcome.IRRELEVANT;
		}
		TronClient.IncomingTransfer verified = tronClient.verifyIncomingTransfer(transfer);
		if (verified.verification() == TronClient.ReceiptVerification.FAILED) {
			vipOrderService.recordTerminalReceiptFailure(matched.id(), verified.txHash());
			log.warn("Terminally failed VIP receipt ignored for order {} and transaction {}",
					matched.orderNo(), verified.txHash());
			return CandidateOutcome.PROCESSED;
		}
		if (verified.verification() != TronClient.ReceiptVerification.CONFIRMED) {
			return CandidateOutcome.DEFER;
		}
		if (!(recovering
				? VipPaymentMatcher.matchesLatePayment(matched, verified, tronProperties.getRequiredConfirmations(), grace)
				: VipPaymentMatcher.matches(matched, verified, tronProperties.getRequiredConfirmations(), grace))) {
			return CandidateOutcome.DEFER;
		}
		try {
			if (recovering) {
				vipOrderService.autoRecover(matched.id(), verified);
			}
			else {
				vipOrderService.autoConfirm(matched.id(), verified);
			}
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
