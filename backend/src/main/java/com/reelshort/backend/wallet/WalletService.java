package com.reelshort.backend.wallet;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.auth.PasswordHasher;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.system.security.CredentialAttemptGuard;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class WalletService {

	private final UserWalletRepository userWalletRepository;
	private final UserAccountRepository userAccountRepository;
	private final SystemConfigService systemConfigService;
	private final com.reelshort.backend.system.concurrency.UserActionLocks userActionLocks;
	private final PasswordHasher passwordHasher;
	private final CredentialAttemptGuard credentialAttemptGuard;

	public WalletService(UserWalletRepository userWalletRepository, UserAccountRepository userAccountRepository,
			SystemConfigService systemConfigService,
			com.reelshort.backend.system.concurrency.UserActionLocks userActionLocks, PasswordHasher passwordHasher,
			CredentialAttemptGuard credentialAttemptGuard) {
		this.userWalletRepository = userWalletRepository;
		this.userAccountRepository = userAccountRepository;
		this.systemConfigService = systemConfigService;
		this.userActionLocks = userActionLocks;
		this.passwordHasher = passwordHasher;
		this.credentialAttemptGuard = credentialAttemptGuard;
	}

	@Transactional(readOnly = true)
	public WalletResponse wallet(UUID userId) {
		UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
		UserAccount user = userAccountRepository.findById(userId).orElse(null);
		String vipUntil = (user != null && user.vipUntil() != null)
				? java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
						.withZone(java.time.ZoneId.systemDefault()).format(user.vipUntil())
				: null;
		String vipPrice = safeConfigValue(SystemConfigRegistry.VIP_PRICE_USDT);
		String collectionAddress = safeConfigValue(SystemConfigRegistry.VIP_COLLECTION_ADDRESS);
		return WalletResponse.withVip(wallet, vipUntil, vipPrice, collectionAddress);
	}

	@Transactional
	public WalletResponse bindOrReplace(UUID userId, String network, String walletAddress, String password) {
		// 锁定检查必须先于任何密码比对，锁定期间即使密码正确也拒绝（429）。
		credentialAttemptGuard.assertNotLocked(userId);
		// H3: 加 userActionLocks 防止与提现 create 并发时 wallet 被覆盖导致网络/地址错配
		return userActionLocks.withUserLock(userId, () -> {
			verifyCurrentPassword(userId, password);
			String normalizedWalletAddress = normalizeWalletAddress(network, walletAddress);
			UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
			if (wallet == null) {
				wallet = UserWallet.create(userId, network, normalizedWalletAddress);
			}
			else {
				wallet.replace(network, normalizedWalletAddress);
			}
			WalletResponse response = WalletResponse.from(userWalletRepository.save(wallet));
			credentialAttemptGuard.recordSuccess(userId);
			return response;
		});
	}

	@Transactional
	public WalletResponse unbind(UUID userId, String password) {
		credentialAttemptGuard.assertNotLocked(userId);
		return userActionLocks.withUserLock(userId, () -> {
			verifyCurrentPassword(userId, password);
			userWalletRepository.findByUserId(userId).ifPresent(userWalletRepository::delete);
			credentialAttemptGuard.recordSuccess(userId);
			return WalletResponse.from(null);
		});
	}

	private void verifyCurrentPassword(UUID userId, String password) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
		if (user.status() != UserStatus.ACTIVE) {
			throw new AdminException(403, "user disabled");
		}
		if (!passwordHasher.matches(password, user.passwordHash())) {
			// 失败计数用 REQUIRES_NEW 落库：本事务回滚也不会丢失。
			credentialAttemptGuard.recordFailure(userId);
			// 403 而非 401：401 会被客户端当作会话过期处理，导致误清登录态。
			throw new AdminException(403, "invalid current password");
		}
	}

	private String normalizeWalletAddress(String network, String walletAddress) {
		String trimmed = walletAddress == null ? "" : walletAddress.trim();
		if (!"ERC20".equals(network)) {
			throw new AdminException(400, "only ERC20 wallets are supported");
		}
		if (!isValidEthereumAddress(trimmed)) {
			throw new AdminException(400, "invalid wallet address for ERC20");
		}
		return trimmed;
	}

	/**
	 * 校验以太坊地址格式：0x 开头 + 40 个十六进制字符。
	 * 不强制 EIP-55 大小写校验（兼容全小写/全大写输入）。
	 */
	private boolean isValidEthereumAddress(String walletAddress) {
		if (walletAddress == null || walletAddress.length() != 42) {
			return false;
		}
		if (!walletAddress.startsWith("0x") && !walletAddress.startsWith("0X")) {
			return false;
		}
		for (int i = 2; i < 42; i++) {
			char c = walletAddress.charAt(i);
			boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
			if (!isHex) {
				return false;
			}
		}
		return !walletAddress.substring(2).chars().allMatch(character -> character == '0');
	}

	private String safeConfigValue(String key) {
		try {
			String value = systemConfigService.stringValue(key);
			return value == null ? "" : value;
		}
		catch (Exception exception) {
			return "";
		}
	}
}
