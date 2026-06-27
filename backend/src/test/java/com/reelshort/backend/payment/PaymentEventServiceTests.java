package com.reelshort.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.order.CreateRechargeOrderRequest;
import com.reelshort.backend.order.RechargeOrderResponse;
import com.reelshort.backend.order.RechargeOrderService;

@SpringBootTest
@Transactional
class PaymentEventServiceTests {

	@Autowired
	private PaymentCallbackService paymentCallbackService;

	@Autowired
	private PaymentEventService paymentEventService;

	@Autowired
	private RechargeOrderService rechargeOrderService;

	@Test
	void filtersEventsInDatabaseAndReturnsNewestFirst() {
		String channel = "service-test-" + UUID.randomUUID();
		RechargeOrderResponse oldOrder = createOrder(990, 99);
		paymentCallbackService.handle(new PaymentCallbackRequest("evt-service-old", oldOrder.orderNo(), channel,
				990));
		RechargeOrderResponse newOrder = createOrder(1990, 199);
		paymentCallbackService.handle(new PaymentCallbackRequest("evt-service-new", newOrder.orderNo(), channel,
				1990));
		RechargeOrderResponse otherChannelOrder = createOrder(2990, 299);
		paymentCallbackService.handle(new PaymentCallbackRequest("evt-service-other", otherChannelOrder.orderNo(),
				"stripe", 2990));

		List<PaymentEventResponse> events = paymentEventService.events(PaymentEventStatus.PROCESSED, " ",
				channel);

		assertThat(events)
				.extracting(PaymentEventResponse::providerEventId)
				.containsExactly("evt-service-new", "evt-service-old");
	}

	private RechargeOrderResponse createOrder(int amountCents, int pointAmount) {
		return rechargeOrderService.create(UUID.randomUUID(), new CreateRechargeOrderRequest(amountCents, pointAmount));
	}
}
