package com.reelshort.backend.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentEventRecorder {

	private final PaymentEventRepository paymentEventRepository;

	PaymentEventRecorder(PaymentEventRepository paymentEventRepository) {
		this.paymentEventRepository = paymentEventRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void recordRejected(PaymentCallbackRequest request, String reason) {
		if (paymentEventRepository.findByProviderEventId(request.providerEventId()).isEmpty()) {
			paymentEventRepository.save(PaymentEvent.rejected(request, reason));
		}
	}
}
