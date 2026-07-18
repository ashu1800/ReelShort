package com.reelshort.backend.order;

public class OrderException extends RuntimeException {

	private final int statusCode;

	public OrderException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
