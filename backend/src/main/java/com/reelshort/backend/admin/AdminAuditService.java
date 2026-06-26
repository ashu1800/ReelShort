package com.reelshort.backend.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {

	private final AdminAuditLogRepository adminAuditLogRepository;

	public AdminAuditService(AdminAuditLogRepository adminAuditLogRepository) {
		this.adminAuditLogRepository = adminAuditLogRepository;
	}

	@Transactional
	public void record(String adminUsername, String action, String targetType, UUID targetId, String summary) {
		adminAuditLogRepository.save(AdminAuditLog.create(adminUsername, action, targetType, targetId, summary));
	}

	@Transactional(readOnly = true)
	public List<AdminAuditLogResponse> logs() {
		return adminAuditLogRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(AdminAuditLogResponse::from)
				.toList();
	}
}
