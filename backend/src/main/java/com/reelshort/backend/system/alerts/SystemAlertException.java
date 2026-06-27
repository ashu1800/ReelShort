package com.reelshort.backend.system.alerts;

public class SystemAlertException extends RuntimeException {

	private final int statusCode;

	public SystemAlertException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
