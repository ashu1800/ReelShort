package com.reelshort.backend.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
