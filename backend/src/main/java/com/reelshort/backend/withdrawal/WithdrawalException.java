package com.reelshort.backend.withdrawal;

public class WithdrawalException extends RuntimeException {

	private final int statusCode;

	public WithdrawalException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
