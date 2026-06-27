package com.reelshort.backend.system.alerts;

import java.time.Instant;
import java.util.UUID;

public record SystemAlertResponse(
		UUID id,
		String alertKey,
		String severity,
		String status,
		String title,
		String detail,
		String firstSeenAt,
		String lastSeenAt,
		String acknowledgedAt,
		String acknowledgedBy,
		String resolvedAt) {

	public static SystemAlertResponse from(SystemAlert alert) {
		return new SystemAlertResponse(
				alert.id(),
				alert.alertKey(),
				alert.severity().name(),
				alert.status().name(),
				alert.title(),
				alert.detail(),
				format(alert.firstSeenAt()),
				format(alert.lastSeenAt()),
				format(alert.acknowledgedAt()),
				alert.acknowledgedBy(),
				format(alert.resolvedAt()));
	}

	private static String format(Instant instant) {
		return instant == null ? null : instant.toString();
	}
}
