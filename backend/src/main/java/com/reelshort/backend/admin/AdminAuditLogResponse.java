package com.reelshort.backend.admin;

import java.util.UUID;

public record AdminAuditLogResponse(
		UUID id,
		String adminUsername,
		String action,
		String targetType,
		UUID targetId,
		String summary,
		String createdAt) {

	public static AdminAuditLogResponse from(AdminAuditLog auditLog) {
		return new AdminAuditLogResponse(auditLog.id(), auditLog.adminUsername(), auditLog.action(),
				auditLog.targetType(), auditLog.targetId(), auditLog.summary(), auditLog.createdAt().toString());
	}
}
