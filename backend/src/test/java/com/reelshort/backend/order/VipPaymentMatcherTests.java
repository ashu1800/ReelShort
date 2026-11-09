package com.reelshort.backend.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
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

	@Test
	void latePaymentRecoveryAcceptsOnlyExpiredOrdersWithinTheOriginalWindow() {
		VipOrder expired = order();
		expired.expire();
		IncomingTransfer paidInTime = transfer(expired, ADDRESS, CONTRACT, expired.payableAmount(),
				expired.createdAt().plusSeconds(10), 20, true);

		// 有效期内已付款、确认晚于过期：管理员可以补单
		assertThat(VipPaymentMatcher.matchesLatePayment(expired, paidInTime, 20)).isTrue();
		// 但补单路径不接受 PENDING（那是正常确认）或已终态订单
		assertThat(VipPaymentMatcher.matchesLatePayment(order(), paidInTime, 20)).isFalse();
		// 付款时间晚于有效期：即使过期后补单也不接受
		assertThat(VipPaymentMatcher.matchesLatePayment(expired,
				transfer(expired, ADDRESS, CONTRACT, expired.payableAmount(), expired.expiresAt().plusNanos(1), 20,
						true), 20)).isFalse();
		// 其余条件与正常确认完全一致
		assertThat(VipPaymentMatcher.matchesLatePayment(expired,
				transfer(expired, ADDRESS, CONTRACT, expired.payableAmount(),
						expired.createdAt().plusSeconds(10), 19, true), 20)).isFalse();
	}

	@Test
	void acceptanceGraceExtendsThePaymentWindowPastTheDisplayedExpiry() {
		Duration grace = Duration.ofMinutes(10);
		VipOrder pending = order();
		IncomingTransfer paidDuringGrace = transfer(pending, ADDRESS, CONTRACT, pending.payableAmount(),
				pending.expiresAt().plusSeconds(60), 20, true);

		// 倒计时已归零但订单还在宽限期内：按时付款仍可自动确认
		assertThat(VipPaymentMatcher.matches(pending, paidDuringGrace, 20, grace)).isTrue();
		// 无宽限时保持原来的严格窗口（3 参重载语义不变）
		assertThat(VipPaymentMatcher.matches(pending, paidDuringGrace, 20)).isFalse();
		// 越过宽限期仍不付款则无效
		assertThat(VipPaymentMatcher.matches(pending,
				transfer(pending, ADDRESS, CONTRACT, pending.payableAmount(),
						pending.expiresAt().plus(grace).plusNanos(1), 20, true), 20, grace)).isFalse();

		VipOrder expired = order();
		expired.expire();
		assertThat(VipPaymentMatcher.matchesLatePayment(expired, paidDuringGrace, 20, grace)).isTrue();
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
