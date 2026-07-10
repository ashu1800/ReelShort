package com.reelshort.backend.auth;

public class SmsAccountNotFoundException extends SmsCallbackException {

	public SmsAccountNotFoundException(String message) {
		super(message);
	}
}
