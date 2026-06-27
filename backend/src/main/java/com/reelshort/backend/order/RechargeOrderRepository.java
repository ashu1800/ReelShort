package com.reelshort.backend.order;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RechargeOrderRepository extends JpaRepository<RechargeOrder, UUID> {

	List<RechargeOrder> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

	List<RechargeOrder> findAllByOrderByCreatedAtDescIdDesc();
}
