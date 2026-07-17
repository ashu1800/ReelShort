package com.reelshort.backend.order;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;

@Service
public class VipEntitlementService {
	private static final int VIP_DURATION_DAYS = 30;

	private final UserAccountRepository userAccountRepository;

	public VipEntitlementService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	public void grantMonth(UUID userId) {
		UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime base = user.vipUntil() != null && user.vipUntil().isAfter(now) ? user.vipUntil() : now;
		user.grantVip(base.plusDays(VIP_DURATION_DAYS));
		userAccountRepository.save(user);
	}
}
