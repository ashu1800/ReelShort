package com.reelshort.backend.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

/**
 * P1-5: 凭证失败锁定行为。
 */
@SpringBootTest
class CredentialAttemptGuardTests {

	@Autowired
	private CredentialAttemptGuard credentialAttemptGuard;

	@Autowired
	private CredentialAttemptRepository credentialAttemptRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private UserAccount user;

	@BeforeEach
	void setUp() {
		user = userAccountRepository.save(
				UserAccount.create("credential-guard-" + UUID.randomUUID(), "hash", UserStatus.ACTIVE));
	}

	@Test
	void locksAfterFiveFailuresAndRejectsFurtherAttempts() {
		for (int attempt = 0; attempt < 5; attempt++) {
			credentialAttemptGuard.recordFailure(user.id());
		}

		assertThatThrownBy(() -> credentialAttemptGuard.assertNotLocked(user.id()))
				.isInstanceOf(CredentialLockedException.class)
				.hasMessage("too many attempts, try again later");
		// 锁定后计数清零：锁定到期后重新获得完整预算
		assertThat(credentialAttemptRepository.findById(user.id()).orElseThrow().failedCount()).isZero();
		assertThat(credentialAttemptRepository.findById(user.id()).orElseThrow().isLocked()).isTrue();
	}

	@Test
	void belowThresholdDoesNotLock() {
		for (int attempt = 0; attempt < 4; attempt++) {
			credentialAttemptGuard.recordFailure(user.id());
		}

		assertThatCode(() -> credentialAttemptGuard.assertNotLocked(user.id())).doesNotThrowAnyException();
		assertThat(credentialAttemptRepository.findById(user.id()).orElseThrow().failedCount()).isEqualTo(4);
	}

	@Test
	void recordSuccessClearsFailureCounter() {
		credentialAttemptGuard.recordFailure(user.id());
		credentialAttemptGuard.recordFailure(user.id());
		assertThat(credentialAttemptRepository.findById(user.id())).isPresent();

		credentialAttemptGuard.recordSuccess(user.id());

		assertThat(credentialAttemptRepository.findById(user.id())).isEmpty();
		assertThatCode(() -> credentialAttemptGuard.assertNotLocked(user.id())).doesNotThrowAnyException();
	}

	@Test
	void recordFailureSurvivesOuterTransactionRollback() {
		// REQUIRES_NEW：外层业务事务回滚（例如登录/改密失败）后失败计数必须仍然存在
		TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> outerTransaction.execute(status -> {
			credentialAttemptGuard.recordFailure(user.id());
			throw new IllegalStateException("outer business failure");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(credentialAttemptRepository.findById(user.id()).orElseThrow().failedCount()).isEqualTo(1);
	}

	@Test
	void assertNotLockedAllowsUnknownUserWithoutRecord() {
		assertThatCode(() -> credentialAttemptGuard.assertNotLocked(UUID.randomUUID())).doesNotThrowAnyException();
	}
}
