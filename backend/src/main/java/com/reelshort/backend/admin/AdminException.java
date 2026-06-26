package com.reelshort.backend.admin;

public class AdminException extends RuntimeException {

	private final int statusCode;

	public AdminException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
