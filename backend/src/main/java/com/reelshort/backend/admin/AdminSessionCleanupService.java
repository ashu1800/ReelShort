package com.reelshort.backend.admin;

import java.time.OffsetDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSessionCleanupService {

	private final AdminTokenRepository adminTokenRepository;
	private final AdminSessionProperties adminSessionProperties;

	public AdminSessionCleanupService(AdminTokenRepository adminTokenRepository,
			AdminSessionProperties adminSessionProperties) {
		this.adminTokenRepository = adminTokenRepository;
		this.adminSessionProperties = adminSessionProperties;
	}

	@Scheduled(
			initialDelayString = "${reelshort.admin.session.cleanup-initial-delay:1h}",
			fixedDelayString = "${reelshort.admin.session.cleanup-interval:1h}")
	@Transactional
	public void cleanupExpiredAndRevokedTokens() {
		cleanup(OffsetDateTime.now());
	}

	@Transactional
	int cleanup(OffsetDateTime now) {
		OffsetDateTime cutoff = now.minus(adminSessionProperties.getCleanupRetention());
		return adminTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
	}
}
