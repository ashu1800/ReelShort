package com.reelshort.backend.order;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.points.PointsService;

@Service
public class RechargeOrderService {

	private final RechargeOrderRepository rechargeOrderRepository;
	private final PointsService pointsService;
	private final OrderSettlementLocks orderSettlementLocks;

	public RechargeOrderService(RechargeOrderRepository rechargeOrderRepository, PointsService pointsService,
			OrderSettlementLocks orderSettlementLocks) {
		this.rechargeOrderRepository = rechargeOrderRepository;
		this.pointsService = pointsService;
		this.orderSettlementLocks = orderSettlementLocks;
	}

	@Transactional
	public RechargeOrderResponse create(UUID userId, CreateRechargeOrderRequest request) {
		validate(request);
		RechargeOrder order = RechargeOrder.create(userId, orderNo(), request.amountCents(), request.pointAmount());
		return RechargeOrderResponse.from(rechargeOrderRepository.save(order));
	}

	@Transactional(readOnly = true)
	public List<RechargeOrderResponse> userOrders(UUID userId) {
		return rechargeOrderRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
				.map(RechargeOrderResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<RechargeOrderResponse> allOrders() {
		return rechargeOrderRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
				.map(RechargeOrderResponse::from)
				.toList();
	}

	@Transactional
	public RechargeOrderResponse settlePaid(String orderNo, String paymentChannel) {
		return orderSettlementLocks.withOrderLock(orderNo, () -> settlePaidLocked(orderNo, paymentChannel));
	}

	private RechargeOrderResponse settlePaidLocked(String orderNo, String paymentChannel) {
		RechargeOrder order = rechargeOrderRepository.findByOrderNo(orderNo)
				.orElseThrow(() -> new IllegalArgumentException("order not found"));
		if (order.isPaid()) {
			return RechargeOrderResponse.from(order);
		}
		order.markPaid(paymentChannel);
		pointsService.creditRechargeOrder(order.userId(), order.orderNo(), order.pointAmount());
		return RechargeOrderResponse.from(rechargeOrderRepository.save(order));
	}

	private void validate(CreateRechargeOrderRequest request) {
		if (request.amountCents() <= 0) {
			throw new IllegalArgumentException("amountCents must be positive");
		}
		if (request.pointAmount() <= 0) {
			throw new IllegalArgumentException("pointAmount must be positive");
		}
	}

	private String orderNo() {
		String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(OffsetDateTime.now());
		return "RO" + timestamp + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
	}
}
