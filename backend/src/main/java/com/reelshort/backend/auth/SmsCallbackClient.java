package com.reelshort.backend.auth;

public interface SmsCallbackClient {

	void send(SmsCallbackMessage message);
}
