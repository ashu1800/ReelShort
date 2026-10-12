package com.reelshort.backend.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CaptchaChallengeRepository extends JpaRepository<CaptchaChallenge, UUID> {

	/**
	 * M5: 悲观锁读取，防止验证码并发双花（TOCTOU）。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM CaptchaChallenge c WHERE c.id = :id")
	Optional<CaptchaChallenge> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * P2-16: 清理任务使用的批量删除（不走实体逐条删除，避免把过期行全部加载进内存）。
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			delete from CaptchaChallenge challenge
			where challenge.expiresAt < :cutoff
			""")
	int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
