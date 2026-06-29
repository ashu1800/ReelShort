package com.reelshort.backend.social;

public class SocialException extends RuntimeException {

	private final int statusCode;

	public SocialException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
