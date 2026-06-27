package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.points.PointAccountRepository;

@SpringBootTest
class RechargeOrderServiceTests {

	@Autowired
	private RechargeOrderService rechargeOrderService;

	@Autowired
	private RechargeOrderRepository rechargeOrderRepository;

	@Autowired
	private PointAccountRepository pointAccountRepository;

	@Test
	void createsRechargeOrderWithCreatedStatus() {
		UUID userId = UUID.randomUUID();

		RechargeOrderResponse response = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));

		assertThat(response.id()).isNotNull();
		assertThat(response.orderNo()).startsWith("RO");
		assertThat(response.amountCents()).isEqualTo(990);
		assertThat(response.pointAmount()).isEqualTo(99);
		assertThat(response.status()).isEqualTo(RechargeOrderStatus.CREATED);
		assertThat(response.paymentChannel()).isNull();
		assertThat(response.createdAt()).isNotBlank();
		assertThat(rechargeOrderRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId)).hasSize(1);
	}

	@Test
	void createDoesNotCreatePointAccountOrBalance() {
		UUID userId = UUID.randomUUID();

		rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));

		assertThat(pointAccountRepository.findByUserId(userId)).isEmpty();
	}

	@Test
	void rejectsInvalidAmountAndPoints() {
		UUID userId = UUID.randomUUID();

		assertThatThrownBy(() -> rechargeOrderService.create(userId, new CreateRechargeOrderRequest(0, 99)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("amountCents must be positive");

		assertThatThrownBy(() -> rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 0)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("pointAmount must be positive");
	}

	@Test
	void listsUserOrdersNewestFirst() {
		UUID userId = UUID.randomUUID();
		rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));
		rechargeOrderService.create(userId, new CreateRechargeOrderRequest(1990, 199));
		rechargeOrderService.create(UUID.randomUUID(), new CreateRechargeOrderRequest(2990, 299));

		assertThat(rechargeOrderService.userOrders(userId))
				.extracting(RechargeOrderResponse::amountCents)
				.containsExactly(1990, 990);
	}
}
