package com.reelshort.backend.system.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CredentialAttemptRepository extends JpaRepository<CredentialAttempt, UUID> {

	/**
	 * 悲观锁读取，避免并发失败请求互相覆盖计数（丢计数等于放大爆破次数）。
	 * 行不存在时数据库不会加锁，并发首次插入由主键 + 外键约束兜底。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM CredentialAttempt c WHERE c.userId = :userId")
	Optional<CredentialAttempt> findByUserIdForUpdate(@Param("userId") UUID userId);
}
