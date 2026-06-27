package com.reelshort.backend.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, UUID> {

	List<RechargeOrder> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

	List<RechargeOrder> findAllByOrderByCreatedAtDescIdDesc();

	Optional<RechargeOrder> findByOrderNo(String orderNo);

	long countByStatus(RechargeOrderStatus status);

	@Query("select coalesce(sum(order.amountCents), 0) from RechargeOrder order")
	long sumAmountCents();
}
