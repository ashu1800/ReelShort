package com.reelshort.backend.auth;

public class SmsCallbackException extends RuntimeException {

	public SmsCallbackException(String message) {
		super(message);
	}

	public SmsCallbackException(String message, Throwable cause) {
		super(message, cause);
	}
}
