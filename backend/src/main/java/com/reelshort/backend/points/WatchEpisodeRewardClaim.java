package com.reelshort.backend.points;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "watch_episode_reward_claims", uniqueConstraints = {
		@UniqueConstraint(name = "uk_watch_episode_reward_claim", columnNames = { "user_id", "book_id", "episode_num" })
})
class WatchEpisodeRewardClaim {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "episode_num", nullable = false)
	private int episodeNum;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	@Column(name = "calculated_points", nullable = false)
	private int calculatedPoints;

	@Column(name = "calculated_tenths", nullable = false)
	private int calculatedTenths;

	@Column(name = "awarded_points", nullable = false)
	private int awardedPoints;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected WatchEpisodeRewardClaim() {
	}

	private WatchEpisodeRewardClaim(UUID id, UUID userId, String bookId, int episodeNum, int durationSeconds,
			int calculatedPoints, int calculatedTenths, int awardedPoints, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.bookId = bookId;
		this.episodeNum = episodeNum;
		this.durationSeconds = durationSeconds;
		this.calculatedPoints = calculatedPoints;
		this.calculatedTenths = calculatedTenths;
		this.awardedPoints = awardedPoints;
		this.createdAt = createdAt;
	}

	static WatchEpisodeRewardClaim create(UUID userId, String bookId, int episodeNum, int durationSeconds,
			int calculatedTenths, int awardedPoints, OffsetDateTime createdAt) {
		return new WatchEpisodeRewardClaim(UUID.randomUUID(), userId, bookId, episodeNum, durationSeconds,
				calculatedTenths / WatchRewardCalculation.FAIR_MODE_SCALE, calculatedTenths, awardedPoints, createdAt);
	}
}
