package com.reelshort.backend.points;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "watch_reward_claims", uniqueConstraints = {
		@UniqueConstraint(name = "uk_watch_reward_claims_stage", columnNames = {
				"user_id", "book_id", "episode_num", "stage"
		})
})
public class WatchRewardClaim {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "episode_num", nullable = false)
	private int episodeNum;

	@Column(nullable = false)
	private int stage;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected WatchRewardClaim() {
	}

	private WatchRewardClaim(UUID id, UUID userId, String bookId, int episodeNum, int stage, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.bookId = bookId;
		this.episodeNum = episodeNum;
		this.stage = stage;
		this.createdAt = createdAt;
	}

	public static WatchRewardClaim create(UUID userId, String bookId, int episodeNum, int stage) {
		return new WatchRewardClaim(UUID.randomUUID(), userId, bookId, episodeNum, stage, OffsetDateTime.now());
	}
}
