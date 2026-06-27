package com.reelshort.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.reelshort.backend.order.CreateRechargeOrderRequest;
import com.reelshort.backend.order.RechargeOrderRepository;
import com.reelshort.backend.order.RechargeOrderResponse;
import com.reelshort.backend.order.RechargeOrderService;
import com.reelshort.backend.order.RechargeOrderStatus;
import com.reelshort.backend.points.PointsService;

@SpringBootTest
class PaymentCallbackServiceTests {

	@Autowired
	private PaymentCallbackService paymentCallbackService;

	@Autowired
	private PaymentEventRepository paymentEventRepository;

	@Autowired
	private RechargeOrderService rechargeOrderService;

	@Autowired
	private RechargeOrderRepository rechargeOrderRepository;

	@Autowired
	private PointsService pointsService;

	@Test
	void successfulCallbackSettlesOrderAndCreditsPoints() {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse order = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));

		PaymentCallbackResponse response = paymentCallbackService.handle(
				new PaymentCallbackRequest("evt-1", order.orderNo(), "mock-pay", 990));

		assertThat(response.status()).isEqualTo(PaymentEventStatus.PROCESSED);
		assertThat(response.orderStatus()).isEqualTo(RechargeOrderStatus.PAID);
		assertThat(pointsService.account(userId).balance()).isEqualTo(99);
		assertThat(paymentEventRepository.findByProviderEventId("evt-1")).isPresent();
	}

	@Test
	void duplicateCallbackEventDoesNotSettleTwice() {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse order = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));
		PaymentCallbackRequest request = new PaymentCallbackRequest("evt-duplicate", order.orderNo(), "mock-pay", 990);

		PaymentCallbackResponse first = paymentCallbackService.handle(request);
		PaymentCallbackResponse second = paymentCallbackService.handle(request);

		assertThat(first.status()).isEqualTo(PaymentEventStatus.PROCESSED);
		assertThat(second.status()).isEqualTo(PaymentEventStatus.PROCESSED);
		assertThat(pointsService.account(userId).balance()).isEqualTo(99);
		assertThat(pointsService.records(userId)).hasSize(1);
		assertThat(paymentEventRepository.findByProviderEventId("evt-duplicate")).isPresent();
	}

	@Test
	void concurrentDuplicateCallbackEventDoesNotSettleTwice() throws Exception {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse order = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));
		PaymentCallbackRequest request = new PaymentCallbackRequest("evt-concurrent-duplicate", order.orderNo(),
				"mock-pay", 990);
		List<PaymentCallbackRequest> requests = List.of(request, request);

		List<PaymentCallbackResponse> responses = handleConcurrently(requests);

		assertThat(responses).extracting(PaymentCallbackResponse::status)
				.containsExactly(PaymentEventStatus.PROCESSED, PaymentEventStatus.PROCESSED);
		assertThat(pointsService.account(userId).balance()).isEqualTo(99);
		assertThat(pointsService.records(userId)).hasSize(1);
		assertThat(paymentEventRepository.findByProviderEventId("evt-concurrent-duplicate")).isPresent();
	}

	@Test
	void concurrentDifferentEventsForSameOrderDoNotSettleTwice() throws Exception {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse order = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));
		List<PaymentCallbackRequest> requests = List.of(
				new PaymentCallbackRequest("evt-same-order-1", order.orderNo(), "mock-pay", 990),
				new PaymentCallbackRequest("evt-same-order-2", order.orderNo(), "mock-pay", 990));

		List<PaymentCallbackResponse> responses = handleConcurrently(requests);

		assertThat(responses).extracting(PaymentCallbackResponse::status)
				.containsExactly(PaymentEventStatus.PROCESSED, PaymentEventStatus.PROCESSED);
		assertThat(pointsService.account(userId).balance()).isEqualTo(99);
		assertThat(pointsService.records(userId)).hasSize(1);
		assertThat(paymentEventRepository.findByProviderEventId("evt-same-order-1")).isPresent();
		assertThat(paymentEventRepository.findByProviderEventId("evt-same-order-2")).isPresent();
	}

	private List<PaymentCallbackResponse> handleConcurrently(List<PaymentCallbackRequest> requests)
			throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(requests.size());
		CountDownLatch ready = new CountDownLatch(requests.size());
		CountDownLatch start = new CountDownLatch(1);

		try {
			List<Future<PaymentCallbackResponse>> futures = new ArrayList<>();
			for (PaymentCallbackRequest request : requests) {
				Callable<PaymentCallbackResponse> callback = () -> {
					ready.countDown();
					start.await();
					return paymentCallbackService.handle(request);
				};
				futures.add(executor.submit(callback));
			}
			ready.await();
			start.countDown();

			List<PaymentCallbackResponse> responses = new ArrayList<>();
			for (Future<PaymentCallbackResponse> future : futures) {
				responses.add(future.get());
			}
			return responses;
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void amountMismatchRejectsEventAndDoesNotSettleOrder() {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse order = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));

		assertThatThrownBy(() -> paymentCallbackService.handle(
				new PaymentCallbackRequest("evt-mismatch", order.orderNo(), "mock-pay", 1990)))
				.isInstanceOf(PaymentException.class)
				.hasMessage("payment amount mismatch");

		assertThat(rechargeOrderService.userOrders(userId).get(0).status()).isEqualTo(RechargeOrderStatus.CREATED);
		assertThat(pointsService.records(userId)).isEmpty();
		assertThat(paymentEventRepository.findByProviderEventId("evt-mismatch"))
				.hasValueSatisfying(event -> assertThat(event.status()).isEqualTo(PaymentEventStatus.REJECTED));
	}

	@Test
	void callbackForCancelledOrderIsRejectedAndRecorded() {
		UUID userId = UUID.randomUUID();
		RechargeOrderResponse order = rechargeOrderService.create(userId, new CreateRechargeOrderRequest(990, 99));
		rechargeOrderRepository.findByOrderNo(order.orderNo()).ifPresent(cancelled -> {
			ReflectionTestUtils.invokeMethod(cancelled, "cancel");
			rechargeOrderRepository.saveAndFlush(cancelled);
		});

		assertThatThrownBy(() -> paymentCallbackService.handle(
				new PaymentCallbackRequest("evt-cancelled", order.orderNo(), "mock-pay", 990)))
				.isInstanceOf(PaymentException.class)
				.hasMessage("order cannot be settled from status CANCELLED");

		assertThat(pointsService.records(userId)).isEmpty();
		assertThat(paymentEventRepository.findByProviderEventId("evt-cancelled"))
				.hasValueSatisfying(event -> assertThat(event.status()).isEqualTo(PaymentEventStatus.REJECTED));
	}
}
