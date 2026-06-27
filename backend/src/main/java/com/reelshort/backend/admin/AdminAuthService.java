package com.reelshort.backend.admin;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.auth.PasswordHasher;
import com.reelshort.backend.auth.TokenHasher;

@Service
public class AdminAuthService {

	private final SecureRandom secureRandom = new SecureRandom();

	private final AdminProperties adminProperties;
	private final AdminUserRepository adminUserRepository;
	private final AdminTokenRepository adminTokenRepository;
	private final PasswordHasher passwordHasher;
	private final TokenHasher tokenHasher;

	public AdminAuthService(AdminProperties adminProperties, AdminUserRepository adminUserRepository,
			AdminTokenRepository adminTokenRepository, PasswordHasher passwordHasher, TokenHasher tokenHasher) {
		this.adminProperties = adminProperties;
		this.adminUserRepository = adminUserRepository;
		this.adminTokenRepository = adminTokenRepository;
		this.passwordHasher = passwordHasher;
		this.tokenHasher = tokenHasher;
	}

	@Transactional
	public AdminAuthTokenResponse login(String username, String password) {
		String normalizedUsername = username.trim();
		AdminUser admin = adminUserRepository.findByUsername(normalizedUsername)
				.orElseThrow(() -> new AuthException(401, "invalid username or password"));
		if (admin.status() != AdminUserStatus.ACTIVE || !passwordHasher.matches(password, admin.passwordHash())) {
			throw new AuthException(401, "invalid username or password");
		}
		byte[] tokenBytes = new byte[32];
		secureRandom.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		adminTokenRepository.save(AdminToken.issue(tokenHasher.hash(token), admin.id(), normalizedUsername,
				OffsetDateTime.now().plus(adminProperties.tokenTtl())));
		return new AdminAuthTokenResponse(normalizedUsername, token, "Bearer");
	}
}
