package com.reelshort.backend.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.order.RechargeOrderRepository;
import com.reelshort.backend.order.RechargeOrderResponse;
import com.reelshort.backend.order.RechargeOrderService;

@Service
public class PaymentCallbackService {

	private final PaymentEventRepository paymentEventRepository;
	private final PaymentEventRecorder paymentEventRecorder;
	private final PaymentEventLocks paymentEventLocks;
	private final RechargeOrderRepository rechargeOrderRepository;
	private final RechargeOrderService rechargeOrderService;

	public PaymentCallbackService(PaymentEventRepository paymentEventRepository,
			PaymentEventRecorder paymentEventRecorder, PaymentEventLocks paymentEventLocks,
			RechargeOrderRepository rechargeOrderRepository, RechargeOrderService rechargeOrderService) {
		this.paymentEventRepository = paymentEventRepository;
		this.paymentEventRecorder = paymentEventRecorder;
		this.paymentEventLocks = paymentEventLocks;
		this.rechargeOrderRepository = rechargeOrderRepository;
		this.rechargeOrderService = rechargeOrderService;
	}

	@Transactional
	public PaymentCallbackResponse handle(PaymentCallbackRequest request) {
		return paymentEventLocks.withEventLock(request.providerEventId(), () -> handleLocked(request));
	}

	private PaymentCallbackResponse handleLocked(PaymentCallbackRequest request) {
		return paymentEventRepository.findByProviderEventId(request.providerEventId())
				.map(this::existingResponse)
				.orElseGet(() -> processNewEvent(request));
	}

	private PaymentCallbackResponse existingResponse(PaymentEvent event) {
		if (event.status() == PaymentEventStatus.REJECTED) {
			return PaymentCallbackResponse.rejected(event);
		}
		return PaymentCallbackResponse.processed(event.providerEventId(), event.orderNo(),
				rechargeOrderRepository.findByOrderNo(event.orderNo()).orElseThrow().status());
	}

	private PaymentCallbackResponse processNewEvent(PaymentCallbackRequest request) {
		RechargeOrderResponse settled;
		try {
			settled = rechargeOrderService.settlePaid(request.orderNo(), request.paymentChannel(),
					request.amountCents());
		}
		catch (IllegalArgumentException | IllegalStateException exception) {
			throw reject(request, exception.getMessage());
		}
		paymentEventRepository.save(PaymentEvent.processed(request));
		return PaymentCallbackResponse.processed(request.providerEventId(), request.orderNo(), settled.status());
	}

	private PaymentException reject(PaymentCallbackRequest request, String reason) {
		paymentEventRecorder.recordRejected(request, reason);
		return new PaymentException(400, reason);
	}
}
