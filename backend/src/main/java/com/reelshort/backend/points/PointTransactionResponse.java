package com.reelshort.backend.points;

import java.util.UUID;

public record PointTransactionResponse(
		UUID id,
		int amount,
		int balanceAfter,
		String source,
		String bookId,
		Integer episodeNum,
		Integer stage,
		String reason,
		String createdAt) {

	public static PointTransactionResponse from(PointTransaction transaction) {
		return new PointTransactionResponse(transaction.id(), transaction.amount(), transaction.balanceAfter(),
				transaction.source(), transaction.bookId(), transaction.episodeNum(), transaction.stage(),
				transaction.reason(), transaction.createdAt().toString());
	}
}
