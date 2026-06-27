package com.reelshort.backend.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class AuthService {

	private final UserAccountRepository userAccountRepository;
	private final PasswordHasher passwordHasher;
	private final TokenService tokenService;

	public AuthService(UserAccountRepository userAccountRepository, PasswordHasher passwordHasher,
			TokenService tokenService) {
		this.userAccountRepository = userAccountRepository;
		this.passwordHasher = passwordHasher;
		this.tokenService = tokenService;
	}

	@Transactional
	public AuthToken register(String username, String password) {
		String normalizedUsername = normalizeUsername(username);
		if (userAccountRepository.existsByUsername(normalizedUsername)) {
			throw new AuthException(409, "username already exists");
		}
		UserAccount user = UserAccount.create(normalizedUsername, passwordHasher.hash(password), UserStatus.ACTIVE);
		try {
			return tokenService.issue(userAccountRepository.save(user));
		}
		catch (DataIntegrityViolationException exception) {
			throw new AuthException(409, "username already exists");
		}
	}

	@Transactional
	public AuthToken login(String username, String password) {
		String normalizedUsername = normalizeUsername(username);
		UserAccount user = userAccountRepository.findByUsername(normalizedUsername)
				.orElseThrow(() -> new AuthException(401, "invalid username or password"));
		if (user.status() == UserStatus.DISABLED) {
			throw new AuthException(403, "user disabled");
		}
		if (!passwordHasher.matches(password, user.passwordHash())) {
			throw new AuthException(401, "invalid username or password");
		}
		return tokenService.issue(user);
	}

	@Transactional
	public void logout(String token) {
		tokenService.revoke(token);
	}

	private String normalizeUsername(String username) {
		return username.trim();
	}
}
