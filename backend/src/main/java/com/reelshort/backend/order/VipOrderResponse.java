package com.reelshort.backend.order;

/**
 * Response DTO for VIP orders. All numeric fields serialized as String to match Android client's
 * kotlinx.serialization expectations.
 *
 * @param payableAmount the exact USDT amount the user must transfer (includes unique suffix)
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
		String confirmedAt) {

	public static VipOrderResponse from(VipOrder order) {
		return new VipOrderResponse(
				order.id().toString(),
				order.orderNo(),
				order.usdtAmount().stripTrailingZeros().toPlainString(),
				order.payableAmount().stripTrailingZeros().toPlainString(),
				order.status(),
				order.paymentMethod(),
				order.txHash(),
				order.createdAt().toString(),
				order.confirmedAt() == null ? null : order.confirmedAt().toString());
	}
}
