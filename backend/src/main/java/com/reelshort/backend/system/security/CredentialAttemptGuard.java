package com.reelshort.backend.system.security;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * P1-5: 凭证失败锁定守卫。改密与钱包改绑密码共用同一用户计数。
 *
 * 调用约定（接口冻结，Lead 在 WalletService 中按此调用）：
 * <ul>
 * <li>{@link #assertNotLocked(UUID)} 必须优先于任何 BCrypt 密码比对执行；</li>
 * <li>{@link #recordFailure(UUID)} 使用 REQUIRES_NEW，保证外层业务事务回滚后失败计数仍然落库；</li>
 * <li>{@link #recordSuccess(UUID)} 删除该用户的失败记录。</li>
 * </ul>
 */
@Service
public class CredentialAttemptGuard {

	private static final Logger log = LoggerFactory.getLogger(CredentialAttemptGuard.class);

	private final CredentialAttemptRepository credentialAttemptRepository;
	private final CredentialGuardProperties credentialGuardProperties;

	public CredentialAttemptGuard(CredentialAttemptRepository credentialAttemptRepository,
			CredentialGuardProperties credentialGuardProperties) {
		this.credentialAttemptRepository = credentialAttemptRepository;
		this.credentialGuardProperties = credentialGuardProperties;
	}

	/**
	 * 锁定期间直接抛 {@link CredentialLockedException}（429），调用方必须在密码比对之前调用。
	 */
	@Transactional(readOnly = true)
	public void assertNotLocked(UUID userId) {
		if (userId == null) {
			return;
		}
		credentialAttemptRepository.findById(userId)
				.filter(CredentialAttempt::isLocked)
				.ifPresent(attempt -> {
					throw new CredentialLockedException();
				});
	}

	/**
	 * REQUIRES_NEW：即使外层事务（业务失败 / 抛异常）回滚，失败计数也必须留下。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(UUID userId) {
		if (userId == null) {
			return;
		}
		Optional<CredentialAttempt> existing = credentialAttemptRepository.findByUserIdForUpdate(userId);
		CredentialAttempt attempt = existing.orElseGet(() -> CredentialAttempt.create(userId));
		OffsetDateTime lockedUntil = attempt.recordFailure(
				credentialGuardProperties.maxFailures(), credentialGuardProperties.lockDuration());
		credentialAttemptRepository.save(attempt);
		if (lockedUntil != null) {
			log.warn("Credential attempts locked after too many failures. userId={} lockedUntil={}", userId,
					lockedUntil);
		}
	}

	/**
	 * REQUIRES_NEW：成功凭证交换后清空失败计数。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordSuccess(UUID userId) {
		if (userId == null) {
			return;
		}
		credentialAttemptRepository.findById(userId).ifPresent(credentialAttemptRepository::delete);
	}
}
