package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class RechargeOrderRepositoryTests {

	@Autowired
	private RechargeOrderRepository rechargeOrderRepository;

	@Test
	void orderNoIsUnique() {
		UUID userId = UUID.randomUUID();
		rechargeOrderRepository.saveAndFlush(RechargeOrder.create(userId, "order-1", 990, 99));

		assertThatThrownBy(() -> rechargeOrderRepository.saveAndFlush(
				RechargeOrder.create(UUID.randomUUID(), "order-1", 1990, 199)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findsUserOrdersNewestFirst() {
		UUID userId = UUID.randomUUID();
		rechargeOrderRepository.saveAndFlush(RechargeOrder.create(userId, "order-old", 990, 99));
		rechargeOrderRepository.saveAndFlush(RechargeOrder.create(userId, "order-new", 1990, 199));
		rechargeOrderRepository.saveAndFlush(RechargeOrder.create(UUID.randomUUID(), "other-user", 2990, 299));

		assertThat(rechargeOrderRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId))
				.extracting(RechargeOrder::orderNo)
				.containsExactly("order-new", "order-old");
	}
}
