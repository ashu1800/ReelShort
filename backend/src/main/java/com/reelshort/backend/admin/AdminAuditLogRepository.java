package com.reelshort.backend.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

	List<AdminAuditLog> findAllByOrderByCreatedAtDesc();
}
