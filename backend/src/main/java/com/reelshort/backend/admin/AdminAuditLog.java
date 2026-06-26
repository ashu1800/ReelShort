package com.reelshort.backend.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {

	@Id
	private UUID id;

	@Column(nullable = false, length = 64)
	private String adminUsername;

	@Column(nullable = false, length = 64)
	private String action;

	@Column(nullable = false, length = 64)
	private String targetType;

	@Column(nullable = false)
	private UUID targetId;

	@Column(nullable = false, length = 512)
	private String summary;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected AdminAuditLog() {
	}

	private AdminAuditLog(UUID id, String adminUsername, String action, String targetType, UUID targetId,
			String summary, OffsetDateTime createdAt) {
		this.id = id;
		this.adminUsername = adminUsername;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.summary = summary;
		this.createdAt = createdAt;
	}

	public static AdminAuditLog create(String adminUsername, String action, String targetType, UUID targetId,
			String summary) {
		return new AdminAuditLog(UUID.randomUUID(), adminUsername, action, targetType, targetId, summary,
				OffsetDateTime.now());
	}

	public UUID id() {
		return id;
	}

	public String adminUsername() {
		return adminUsername;
	}

	public String action() {
		return action;
	}

	public String targetType() {
		return targetType;
	}

	public UUID targetId() {
		return targetId;
	}

	public String summary() {
		return summary;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
