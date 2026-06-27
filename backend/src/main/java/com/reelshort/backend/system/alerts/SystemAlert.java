package com.reelshort.backend.system.alerts;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_alerts")
public class SystemAlert {

	@Id
	private UUID id;

	@Column(name = "alert_key", nullable = false, unique = true, length = 160)
	private String alertKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SystemAlertSeverity severity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SystemAlertStatus status;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(nullable = false, length = 512)
	private String detail;

	@Column(name = "first_seen_at", nullable = false)
	private Instant firstSeenAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "acknowledged_at")
	private Instant acknowledgedAt;

	@Column(name = "acknowledged_by", length = 64)
	private String acknowledgedBy;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	protected SystemAlert() {
	}

	private SystemAlert(String alertKey, SystemAlertSeverity severity, String title, String detail, Instant now) {
		this.id = UUID.randomUUID();
		this.alertKey = alertKey;
		this.severity = severity;
		this.status = SystemAlertStatus.OPEN;
		this.title = title;
		this.detail = detail;
		this.firstSeenAt = now;
		this.lastSeenAt = now;
	}

	public static SystemAlert open(String alertKey, SystemAlertSeverity severity, String title, String detail,
			Instant now) {
		return new SystemAlert(alertKey, severity, title, detail, now);
	}

	public void observe(SystemAlertSeverity severity, String title, String detail, Instant now) {
		this.severity = severity;
		this.title = title;
		this.detail = detail;
		this.lastSeenAt = now;
		if (status == SystemAlertStatus.RESOLVED) {
			status = SystemAlertStatus.OPEN;
			resolvedAt = null;
			acknowledgedAt = null;
			acknowledgedBy = null;
		}
	}

	public void acknowledge(String adminUsername, Instant now) {
		if (status == SystemAlertStatus.OPEN) {
			status = SystemAlertStatus.ACKNOWLEDGED;
			acknowledgedAt = now;
			acknowledgedBy = adminUsername;
		}
	}

	public void resolve(Instant now) {
		if (status != SystemAlertStatus.RESOLVED) {
			status = SystemAlertStatus.RESOLVED;
			resolvedAt = now;
		}
	}

	public UUID id() {
		return id;
	}

	public String alertKey() {
		return alertKey;
	}

	public SystemAlertSeverity severity() {
		return severity;
	}

	public SystemAlertStatus status() {
		return status;
	}

	public String title() {
		return title;
	}

	public String detail() {
		return detail;
	}

	public Instant firstSeenAt() {
		return firstSeenAt;
	}

	public Instant lastSeenAt() {
		return lastSeenAt;
	}

	public Instant acknowledgedAt() {
		return acknowledgedAt;
	}

	public String acknowledgedBy() {
		return acknowledgedBy;
	}

	public Instant resolvedAt() {
		return resolvedAt;
	}
}
