package com.reelshort.backend.points;

/**
 * Point account snapshot returned to the app.
 *
 * <p>
 * {@code freeEpisodes} carries the live {@code vip.free-episodes} system
 * configuration so the app can draw the VIP lock at the same boundary the play
 * endpoint enforces; before it was added the client assumed a hard-coded 7 and
 * silently disagreed with the server whenever an operator changed the value.
 */
public record PointAccountResponse(
		int balance,
		int frozenPoints,
		int availablePoints,
		boolean vip,
		String vipUntil,
		String walletAddress,
		int todayCreditedPoints,
		int freeEpisodes) {

	public static PointAccountResponse from(PointAccount account, int freeEpisodes) {
		return new PointAccountResponse(account.balance(), account.frozenPoints(), account.availablePoints(),
				false, null, null, 0, freeEpisodes);
	}
}
