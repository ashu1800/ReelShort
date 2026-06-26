package com.reelshort.backend.content;

public class ContentProviderException extends RuntimeException {

	private final int statusCode;

	public ContentProviderException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	public ContentProviderException(int statusCode, String message, Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode;
	}

	public int statusCode() {
		return statusCode;
	}
}
