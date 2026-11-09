package com.reelshort.backend.withdrawal;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 超时未审核的提现申请自动拒绝：释放冻结积分，避免一笔没人处理的申请永久冻住用户余额
 * （且用户不能再次提交，因为每用户只允许一笔 PENDING）。
 *
 * <p>超时时长由 {@code reelshort.withdrawal.pending-timeout}（默认 72h）控制；
 * 已进入打款流程的申请会被服务层拒绝，不会误释放资金。
 */
@Service
public class WithdrawalExpiryService {

	private static final Logger log = LoggerFactory.getLogger(WithdrawalExpiryService.class);

	private final WithdrawalService withdrawalService;

	public WithdrawalExpiryService(WithdrawalService withdrawalService) {
		this.withdrawalService = withdrawalService;
	}

	@Scheduled(fixedDelayString = "${reelshort.withdrawal.pending-expiry-interval:3600000}",
			initialDelayString = "${reelshort.withdrawal.pending-expiry-initial-delay:60000}")
	public void expireStaleWithdrawals() {
		for (UUID withdrawalId : withdrawalService.stalePendingWithdrawalIds()) {
			try {
				withdrawalService.expireStale(withdrawalId);
			}
			catch (RuntimeException exception) {
				log.warn("Failed to expire stale withdrawal {}: {}", withdrawalId, exception.getMessage());
			}
		}
	}
}
