package com.reelshort.backend.order;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VipOrderRepository extends JpaRepository<VipOrder, UUID> {
	List<VipOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);
	List<VipOrder> findAllByOrderByCreatedAtDesc();
}
