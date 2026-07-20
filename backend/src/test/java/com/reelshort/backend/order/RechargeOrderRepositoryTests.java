package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

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
		OffsetDateTime base = OffsetDateTime.parse("2026-07-20T00:00:00Z");
		RechargeOrder oldOrder = RechargeOrder.create(userId, "order-old", 990, 99);
		RechargeOrder newOrder = RechargeOrder.create(userId, "order-new", 1990, 199);
		ReflectionTestUtils.setField(oldOrder, "createdAt", base);
		ReflectionTestUtils.setField(newOrder, "createdAt", base.plusSeconds(1));
		rechargeOrderRepository.saveAndFlush(oldOrder);
		rechargeOrderRepository.saveAndFlush(newOrder);
		rechargeOrderRepository.saveAndFlush(RechargeOrder.create(UUID.randomUUID(), "other-user", 2990, 299));

		assertThat(rechargeOrderRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId))
				.extracting(RechargeOrder::orderNo)
				.containsExactly("order-new", "order-old");
	}
}
