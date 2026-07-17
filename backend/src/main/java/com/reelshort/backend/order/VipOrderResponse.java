package com.reelshort.backend.order;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Response DTO for VIP orders. All numeric fields serialized as String to match Android client's
 * kotlinx.serialization expectations. Timestamps formatted as yyyy-MM-dd HH:mm:ss.
 */
public record VipOrderResponse(
		String id,
		String userId,
		String orderNo,
		String usdtAmount,
		String payableAmount,
		String receivingNetwork,
		String receivingAddress,
		String tokenContract,
		String status,
		String paymentMethod,
		String txHash,
		String confirmedBy,
		int confirmationCount,
		String paymentObservedAt,
		String createdAt,
		String expiresAt,
		String confirmedAt) {

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	public static VipOrderResponse from(VipOrder order) {
		return new VipOrderResponse(
				order.id().toString(),
				order.userId().toString(),
				order.orderNo(),
				order.baseUsdtAmount().stripTrailingZeros().toPlainString(),
				order.payableAmount().stripTrailingZeros().toPlainString(),
				order.receivingNetwork(),
				order.receivingWalletAddress(),
				order.tokenContractAddress(),
				order.status(),
				order.paymentMethod(),
				order.txHash(),
				order.confirmedBy(),
				order.confirmationCount(),
				fmt(order.paymentObservedAt()),
				fmt(order.createdAt()),
				fmt(order.expiresAt()),
				fmt(order.confirmedAt()));
	}

	private static String fmt(OffsetDateTime time) {
		return time == null ? null : FMT.format(time);
	}
}
