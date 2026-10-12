package com.reelshort.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

/**
 * P2-12: 管理操作失败后审计必须仍然存在（审计走 REQUIRES_NEW，不随业务事务回滚）。
 */
@SpringBootTest
class AdminAuditIndependentRecordingTests {

	@Autowired
	private AdminUserService adminUserService;

	@Autowired
	private AdminAuditLogRepository adminAuditLogRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void failedPointAdjustmentStillWritesAuditLog() {
		UserAccount user = activeUser("audit-failed-adjust");
		String adminUsername = "audit-admin-" + UUID.randomUUID();

		assertThatThrownBy(() -> adminUserService.adjustPoints(adminUsername, user.id(), -1, "manual correction",
				"audit-failure-key-" + UUID.randomUUID()))
				.isInstanceOf(AdminException.class)
				.hasMessage("insufficient point balance");

		assertThat(actionsFor(adminUsername)).contains("POINTS_ADJUST_FAILED");
	}

	@Test
	void failedVipSetStillWritesAuditLog() {
		UserAccount user = activeUser("audit-failed-vip");
		String adminUsername = "audit-admin-" + UUID.randomUUID();

		assertThatThrownBy(() -> adminUserService.setVip(adminUsername, user.id(), OffsetDateTime.now().minusDays(1)))
				.isInstanceOf(AdminException.class)
				.hasMessage("vip expiry must be in the future");

		assertThat(actionsFor(adminUsername)).contains("USER_VIP_SET_FAILED");
	}

	@Test
	void failedStatusChangeOnUnknownUserStillWritesAuditLog() {
		String adminUsername = "audit-admin-" + UUID.randomUUID();
		UUID unknownUserId = UUID.randomUUID();

		assertThatThrownBy(() -> adminUserService.changeStatus(adminUsername, unknownUserId, UserStatus.DISABLED))
				.isInstanceOf(AdminException.class)
				.hasMessage("user not found");

		assertThat(adminAuditLogRepository.findAll())
				.filteredOn(log -> adminUsername.equals(log.adminUsername()))
				.singleElement()
				.satisfies(log -> {
					assertThat(log.action()).isEqualTo("USER_STATUS_CHANGE_FAILED");
					assertThat(log.targetId()).isEqualTo(unknownUserId);
				});
	}

	private List<String> actionsFor(String adminUsername) {
		return adminAuditLogRepository.findAll().stream()
				.filter(log -> adminUsername.equals(log.adminUsername()))
				.map(AdminAuditLog::action)
				.toList();
	}

	private UserAccount activeUser(String usernameSeed) {
		return userAccountRepository.save(
				UserAccount.create(usernameSeed + "-" + UUID.randomUUID(), "hash", UserStatus.ACTIVE));
	}
}
