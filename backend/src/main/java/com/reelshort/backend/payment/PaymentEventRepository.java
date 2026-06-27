package com.reelshort.backend.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID>,
		JpaSpecificationExecutor<PaymentEvent> {

	Optional<PaymentEvent> findByProviderEventId(String providerEventId);

	long countByStatus(PaymentEventStatus status);
}
