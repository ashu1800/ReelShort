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
		String orderNo,
		String usdtAmount,
		String payableAmount,
		String status,
		String paymentMethod,
		String txHash,
		String createdAt,
		String expiresAt,
		String confirmedAt) {

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	public static VipOrderResponse from(VipOrder order) {
		return new VipOrderResponse(
				order.id().toString(),
				order.orderNo(),
				order.usdtAmount().stripTrailingZeros().toPlainString(),
				order.payableAmount().stripTrailingZeros().toPlainString(),
				order.status(),
				order.paymentMethod(),
				order.txHash(),
				fmt(order.createdAt()),
				fmt(order.expiresAt()),
				fmt(order.confirmedAt()));
	}

	private static String fmt(OffsetDateTime time) {
		return time == null ? null : FMT.format(time);
	}
}
