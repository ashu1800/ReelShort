package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.reelshort.backend.withdrawal.TronClient.IncomingTransfer;

class VipPaymentMatcherTests {

	private static final String ADDRESS = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE";
	private static final String CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t";

	@Test
	void matchesOnlyCompleteReceiptWithinOrderWindow() {
		VipOrder order = order();
		IncomingTransfer valid = transfer(order, ADDRESS, CONTRACT, order.payableAmount(),
				order.createdAt().plusSeconds(10), 20, true);

		assertThat(VipPaymentMatcher.matches(order, valid, 20)).isTrue();
		assertThat(VipPaymentMatcher.matches(order,
				transfer(order, "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8", CONTRACT, order.payableAmount(),
						order.createdAt().plusSeconds(10), 20, true), 20)).isFalse();
		assertThat(VipPaymentMatcher.matches(order,
				transfer(order, ADDRESS, "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8", order.payableAmount(),
						order.createdAt().plusSeconds(10), 20, true), 20)).isFalse();
		assertThat(VipPaymentMatcher.matches(order,
				transfer(order, ADDRESS, CONTRACT, order.payableAmount().add(new BigDecimal("0.000001")),
						order.createdAt().plusSeconds(10), 20, true), 20)).isFalse();
		assertThat(VipPaymentMatcher.matches(order,
				transfer(order, ADDRESS, CONTRACT, order.payableAmount(), order.createdAt().minusNanos(1), 20, true),
				20)).isFalse();
		assertThat(VipPaymentMatcher.matches(order,
				transfer(order, ADDRESS, CONTRACT, order.payableAmount(), order.expiresAt().plusNanos(1), 20, true),
				20)).isFalse();
		assertThat(VipPaymentMatcher.matches(order,
				transfer(order, ADDRESS, CONTRACT, order.payableAmount(), order.createdAt().plusSeconds(10), 19, true),
				20)).isFalse();
		assertThat(VipPaymentMatcher.matches(order,
				transfer(order, ADDRESS, CONTRACT, order.payableAmount(), order.createdAt().plusSeconds(10), 20, false),
				20)).isFalse();
	}

	private VipOrder order() {
		return VipOrder.create(UUID.randomUUID(), "VIP-matcher", new BigDecimal("15"), 1, 20,
				ADDRESS, CONTRACT);
	}

	private IncomingTransfer transfer(VipOrder order, String recipient, String contract, BigDecimal amount,
			OffsetDateTime observedAt, int confirmations, boolean successful) {
		return new IncomingTransfer("a".repeat(64), amount, recipient, contract, observedAt, confirmations, successful);
	}
}
