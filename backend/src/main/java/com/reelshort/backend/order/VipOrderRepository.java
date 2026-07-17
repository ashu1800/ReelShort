package com.reelshort.backend.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface VipOrderRepository extends JpaRepository<VipOrder, UUID> {
	List<VipOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);
	List<VipOrder> findAllByOrderByCreatedAtDesc();
	List<VipOrder> findByStatusOrderByCreatedAtAsc(String status);

	long count();
	long countByStatus(String status);

	@Query("SELECT COALESCE(SUM(o.usdtAmount), 0) FROM VipOrder o WHERE o.status = 'CONFIRMED'")
	BigDecimal sumConfirmedUsdtAmount();

	@Query("SELECT o.uniqueSuffix FROM VipOrder o WHERE o.status = 'PENDING'")
	List<Integer> findPendingSuffixes();

	/**
	 * M6: 悲观锁读取单个订单，防止 confirm/autoConfirm/reject/expire 并发 TOCTOU。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM VipOrder o WHERE o.id = :id")
	Optional<VipOrder> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * M4: 悲观锁读取所有 PENDING 订单（expireOverdueOrders 用），防止并发过期与确认。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM VipOrder o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
	List<VipOrder> findPendingForUpdate();
}
