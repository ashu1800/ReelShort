package com.reelshort.backend.system.logs;

public class SystemLogException extends RuntimeException {

	private final int statusCode;

	public SystemLogException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public SystemLogException(int statusCode, String message, Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
