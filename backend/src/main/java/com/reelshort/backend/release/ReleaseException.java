package com.reelshort.backend.release;

public class ReleaseException extends RuntimeException {

	private final int statusCode;

	public ReleaseException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
