package com.reelshort.backend.content;

import org.springframework.stereotype.Service;

import com.reelshort.backend.auth.CurrentUser;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;

/**
 * Checks VIP status for content gating. Non-VIP users see limited episodes and earn no rewards.
 */
@Service
public class VipGateService {

	private final UserAccountRepository userAccountRepository;

	public VipGateService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	public boolean isVip(CurrentUser currentUser) {
		if (currentUser == null) {
			return false;
		}
		return userAccountRepository.findById(currentUser.userId())
				.map(UserAccount::isVip)
				.orElse(false);
	}

	public boolean isVip(java.util.UUID userId) {
		return userAccountRepository.findById(userId)
				.map(UserAccount::isVip)
				.orElse(false);
	}
}
