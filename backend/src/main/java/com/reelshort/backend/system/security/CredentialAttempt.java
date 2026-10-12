package com.reelshort.backend.system.security;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * P1-5: 用户维度的凭证失败计数（改密 / 钱包改绑密码共用同一行）。
 * 表由 Flyway V31 创建，user_id 同时是主键与外键。
 */
@Entity
@Table(name = "credential_attempts")
public class CredentialAttempt {

	@Id
	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "failed_count", nullable = false)
	private int failedCount;

	@Column(name = "locked_until")
	private OffsetDateTime lockedUntil;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected CredentialAttempt() {
	}

	private CredentialAttempt(UUID userId, int failedCount, OffsetDateTime lockedUntil, OffsetDateTime updatedAt) {
		this.userId = userId;
		this.failedCount = failedCount;
		this.lockedUntil = lockedUntil;
		this.updatedAt = updatedAt;
	}

	public static CredentialAttempt create(UUID userId) {
		return new CredentialAttempt(userId, 0, null, OffsetDateTime.now());
	}

	public UUID userId() {
		return userId;
	}

	public int failedCount() {
		return failedCount;
	}

	public OffsetDateTime lockedUntil() {
		return lockedUntil;
	}

	public boolean isLocked() {
		return lockedUntil != null && lockedUntil.isAfter(OffsetDateTime.now());
	}

	/**
	 * 累加一次失败。达到上限时设置锁定窗口并清零计数。
	 *
	 * 计数清零（而不是保留 maxFailures）：锁定到期后用户重新获得完整的失败预算，
	 * 避免"解锁后第一次失败立刻再次锁定"造成的永久性拒绝服务。
	 *
	 * @return 本次触发的锁定到期时间；未触发锁定返回 null
	 */
	public OffsetDateTime recordFailure(int maxFailures, Duration lockDuration) {
		OffsetDateTime now = OffsetDateTime.now();
		// 锁定已过期时视为新一轮计数起点
		if (lockedUntil != null && !lockedUntil.isAfter(now)) {
			lockedUntil = null;
			failedCount = 0;
		}
		failedCount++;
		updatedAt = now;
		if (failedCount >= maxFailures) {
			lockedUntil = now.plus(lockDuration);
			failedCount = 0;
			return lockedUntil;
		}
		return null;
	}
}
