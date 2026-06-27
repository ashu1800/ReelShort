package com.reelshort.backend.payment;

public class PaymentException extends RuntimeException {

	private final int statusCode;

	public PaymentException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
