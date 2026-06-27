package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.points.PointAccountRepository;
import com.reelshort.backend.points.PointsService;

@SpringBootTest
class RechargeOrderServiceTests {

	@Autowired
	private RechargeOrderService rechargeOrderService;

	@Autowired
	private RechargeOrderRepository rechargeOrderRepository;

	@Autowired
	private PointAccountRepository pointAccountRepository;

	@Autowired
	private PointsService pointsService;

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

	@Test
	void settlePaidOrderMarksPaidAndCreditsPoints() {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse created = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));

		RechargeOrderResponse paid = rechargeOrderService.settlePaid(created.orderNo(), "test-channel");

		assertThat(paid.status()).isEqualTo(RechargeOrderStatus.PAID);
		assertThat(paid.paymentChannel()).isEqualTo("test-channel");
		assertThat(pointsService.account(userId).balance()).isEqualTo(99);
		assertThat(pointsService.records(userId))
				.singleElement()
				.satisfies(record -> {
					assertThat(record.source()).isEqualTo("RECHARGE_ORDER");
					assertThat(record.amount()).isEqualTo(99);
					assertThat(record.reason()).isEqualTo(created.orderNo());
				});
	}

	@Test
	void duplicateSettlePaidOrderIsIdempotent() {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse created = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));

		rechargeOrderService.settlePaid(created.orderNo(), "test-channel");
		RechargeOrderResponse duplicate = rechargeOrderService.settlePaid(created.orderNo(), "test-channel");

		assertThat(duplicate.status()).isEqualTo(RechargeOrderStatus.PAID);
		assertThat(pointsService.account(userId).balance()).isEqualTo(99);
		assertThat(pointsService.records(userId)).hasSize(1);
	}

	@Test
	void concurrentSettlePaidOrderOnlyCreditsPointsOnce() throws Exception {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse created = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<RechargeOrderResponse> task = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return rechargeOrderService.settlePaid(created.orderNo(), "test-channel");
		};

		Future<RechargeOrderResponse> first = executor.submit(task);
		Future<RechargeOrderResponse> second = executor.submit(task);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		List<RechargeOrderResponse> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
		executor.shutdownNow();

		assertThat(results).extracting(RechargeOrderResponse::status)
				.containsExactly(RechargeOrderStatus.PAID, RechargeOrderStatus.PAID);
		assertThat(pointsService.account(userId).balance()).isEqualTo(99);
		assertThat(pointsService.records(userId)).hasSize(1);
	}

	@Test
	void cancelledOrderCannotBeSettled() {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse created = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));
		RechargeOrder order = rechargeOrderRepository.findByOrderNo(created.orderNo()).orElseThrow();
		order.cancel();
		rechargeOrderRepository.saveAndFlush(order);

		assertThatThrownBy(() -> rechargeOrderService.settlePaid(created.orderNo(), "test-channel"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("order cannot be settled from status CANCELLED");
		assertThat(pointAccountRepository.findByUserId(userId)).isEmpty();
	}
}
