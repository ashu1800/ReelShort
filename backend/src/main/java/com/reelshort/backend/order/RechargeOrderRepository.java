package com.reelshort.backend.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, UUID> {

	List<RechargeOrder> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

	List<RechargeOrder> findAllByOrderByCreatedAtDescIdDesc();

	Optional<RechargeOrder> findByOrderNo(String orderNo);

	/** 结算路径专用：行级悲观锁，保证多请求/多实例下同一订单只入账一次。 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select order from RechargeOrder order where order.orderNo = :orderNo")
	Optional<RechargeOrder> findByOrderNoForUpdate(String orderNo);

	long countByStatus(RechargeOrderStatus status);

	@Query("select coalesce(sum(order.amountCents), 0) from RechargeOrder order")
	long sumAmountCents();
}
