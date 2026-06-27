package com.reelshort.backend.payment;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

@Service
public class PaymentEventService {

	private final PaymentEventRepository paymentEventRepository;

	public PaymentEventService(PaymentEventRepository paymentEventRepository) {
		this.paymentEventRepository = paymentEventRepository;
	}

	@Transactional(readOnly = true)
	public List<PaymentEventResponse> events(PaymentEventStatus status, String orderNo, String paymentChannel) {
		return paymentEventRepository.findAll(specification(status, orderNo, paymentChannel),
				Sort.by(Sort.Direction.DESC, "processedAt", "id")).stream()
				.map(PaymentEventResponse::from)
				.toList();
	}

	private Specification<PaymentEvent> specification(PaymentEventStatus status, String orderNo,
			String paymentChannel) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new java.util.ArrayList<>();
			if (status != null) {
				predicates.add(builder.equal(root.get("status"), status));
			}
			if (!isBlank(orderNo)) {
				predicates.add(builder.equal(root.get("orderNo"), orderNo));
			}
			if (!isBlank(paymentChannel)) {
				predicates.add(builder.equal(root.get("paymentChannel"), paymentChannel));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
