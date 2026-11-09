package com.reelshort.backend.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

	@Query("SELECT COALESCE(SUM(o.payableUsdtAmount), 0) FROM VipOrder o WHERE o.status = 'CONFIRMED'")
	BigDecimal sumConfirmedPayableUsdtAmount();

	@Query("SELECT o.uniqueSuffix FROM VipOrder o WHERE o.status = 'PENDING'")
	List<Integer> findPendingSuffixes();

	boolean existsByTxHash(String txHash);

	List<VipOrder> findByStatusAndExpiresAtAfterOrderByCreatedAtAsc(String status, OffsetDateTime cutoff);

	/** 已过付款期（含宽限）但仍为 PENDING 的订单 ID，供下单/定时任务逐行加锁过期。 */
	@Query("SELECT o.id FROM VipOrder o WHERE o.status = 'PENDING' AND o.expiresAt IS NOT NULL"
			+ " AND o.expiresAt < :cutoff")
	List<UUID> findExpiredPendingIds(@Param("cutoff") OffsetDateTime cutoff);

	@Query(value = "SELECT id FROM vip_order_allocation_lock WHERE id = 1 FOR UPDATE", nativeQuery = true)
	Integer lockAllocation();

	/**
	 * M6: 悲观锁读取单个订单，防止 confirm/autoConfirm/reject/expire 并发 TOCTOU。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM VipOrder o WHERE o.id = :id")
	Optional<VipOrder> findByIdForUpdate(@Param("id") UUID id);
}
