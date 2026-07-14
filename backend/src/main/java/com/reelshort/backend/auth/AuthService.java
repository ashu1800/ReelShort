package com.reelshort.backend.auth;

import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class AuthService {

	private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{3,19}$");
	private static final int PASSWORD_MIN_LENGTH = 6;

	private final UserAccountRepository userAccountRepository;
	private final PasswordHasher passwordHasher;
	private final TokenService tokenService;
	private final CaptchaService captchaService;

	public AuthService(UserAccountRepository userAccountRepository, PasswordHasher passwordHasher,
			TokenService tokenService, CaptchaService captchaService) {
		this.userAccountRepository = userAccountRepository;
		this.passwordHasher = passwordHasher;
		this.tokenService = tokenService;
		this.captchaService = captchaService;
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
		if (user.status() != UserStatus.ACTIVE) {
			throw new AuthException(403, "user disabled");
		}
		if (!passwordHasher.matches(password, user.passwordHash())) {
			throw new AuthException(401, "invalid username or password");
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
		if (!passwordHasher.matches(oldPassword, user.passwordHash())) {
			throw new AuthException(401, "invalid old password");
		}
		validatePassword(newPassword);
		user.changePasswordHash(passwordHasher.hash(newPassword));
		userAccountRepository.save(user);
		tokenService.revokeAllForUser(user.id());
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
