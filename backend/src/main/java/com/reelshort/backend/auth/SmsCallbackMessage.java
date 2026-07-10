package com.reelshort.backend.auth;

import java.time.OffsetDateTime;

public record SmsCallbackMessage(
		String supplierMessageId,
		String phone,
		String content,
		OffsetDateTime receivedAt) {
}
