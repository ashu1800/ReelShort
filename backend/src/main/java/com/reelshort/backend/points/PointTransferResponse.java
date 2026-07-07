package com.reelshort.backend.points;

import java.util.UUID;

public record PointTransferResponse(
		UUID id,
		String direction,
		String senderAccount,
		String recipientAccount,
		int pointAmount,
		String createdAt) {

	public static PointTransferResponse from(PointTransfer transfer, UUID currentUserId) {
		String direction = transfer.senderUserId().equals(currentUserId) ? "OUT" : "IN";
		return new PointTransferResponse(transfer.id(), direction, transfer.senderAccount(),
				transfer.recipientAccount(), transfer.pointAmount(), transfer.createdAt().toString());
	}
}
