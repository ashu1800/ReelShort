package com.reelshort.backend.auth;

import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.system.security.CredentialAttemptGuard;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class AuthService {

	private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{3,19}$");
	private static final int PASSWORD_MIN_LENGTH = 8;

	private final UserAccountRepository userAccountRepository;
	private final PasswordHasher passwordHasher;
	private final TokenService tokenService;
	private final CaptchaService captchaService;
	private final CredentialAttemptGuard credentialAttemptGuard;

	public AuthService(UserAccountRepository userAccountRepository, PasswordHasher passwordHasher,
			TokenService tokenService, CaptchaService captchaService, CredentialAttemptGuard credentialAttemptGuard) {
		this.userAccountRepository = userAccountRepository;
		this.passwordHasher = passwordHasher;
		this.tokenService = tokenService;
		this.captchaService = captchaService;
		this.credentialAttemptGuard = credentialAttemptGuard;
	}

	@Transactional
	public AuthToken register(String username, String password, String captchaId, String captchaAnswer) {
		validateUsername(username);
		validatePassword(password);
		captchaService.verifyAndConsume(captchaId, captchaAnswer);
		if (userAccountRepository.existsByUsername(username)) {
			throw new AuthException(409, "username already exists");
		}
		UserAccount user = UserAccount.create(username, passwordHasher.hash(password), UserStatus.ACTIVE);
		try {
			return tokenService.issue(userAccountRepository.save(user));
		}
		catch (DataIntegrityViolationException exception) {
			throw new AuthException(409, "username already exists");
		}
	}

	@Transactional
	public AuthToken login(String username, String password) {
		UserAccount user = userAccountRepository.findByUsername(username)
				.orElseThrow(() -> new AuthException(401, "invalid username or password"));
		// P2-14: 先做 BCrypt 比对（对禁用账号也执行一次 matches，消除时序差异），密码正确后才判定状态，
		// 否则 403 "user disabled" 会变成"该用户名存在且被封禁"的枚举名单。
		if (!passwordHasher.matches(password, user.passwordHash())) {
			throw new AuthException(401, "invalid username or password");
		}
		if (user.status() != UserStatus.ACTIVE) {
			throw new AuthException(403, "user disabled");
		}
		return tokenService.issue(user);
	}

	@Transactional
	public void changePassword(CurrentUser currentUser, String oldPassword, String newPassword) {
		UserAccount user = userAccountRepository.findById(currentUser.userId())
				.orElseThrow(() -> new AuthException(401, "unauthorized"));
		if (user.status() != UserStatus.ACTIVE) {
			throw new AuthException(403, "user disabled");
		}
		// P1-5: 锁定判断必须优先于密码比对，锁定期间即使密码正确也拒绝（避免继续消耗 BCrypt 与爆破额度）
		credentialAttemptGuard.assertNotLocked(user.id());
		if (!passwordHasher.matches(oldPassword, user.passwordHash())) {
			// REQUIRES_NEW：外层事务回滚也要留下失败计数
			credentialAttemptGuard.recordFailure(user.id());
			throw new AuthException(401, "invalid old password");
		}
		validatePassword(newPassword);
		user.changePasswordHash(passwordHasher.hash(newPassword));
		userAccountRepository.save(user);
		tokenService.revokeAllForUser(user.id());
		credentialAttemptGuard.recordSuccess(user.id());
	}

	@Transactional
	public void logout(String token) {
		tokenService.revoke(token);
	}

	private void validateUsername(String username) {
		if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
			throw new AuthException(400, "username must be 4-20 chars, start with a letter, alphanumeric/underscore only");
		}
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
			throw new AuthException(400, "password must be at least " + PASSWORD_MIN_LENGTH + " characters");
		}
	}
}
