package com.reelshort.backend.wallet;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@Service
public class WalletService {

	private final UserWalletRepository userWalletRepository;
	private final UserAccountRepository userAccountRepository;
	private final BankCardAttemptRepository bankCardAttemptRepository;
	private final SystemConfigService systemConfigService;

	public WalletService(UserWalletRepository userWalletRepository, UserAccountRepository userAccountRepository,
			BankCardAttemptRepository bankCardAttemptRepository, SystemConfigService systemConfigService) {
		this.userWalletRepository = userWalletRepository;
		this.userAccountRepository = userAccountRepository;
		this.bankCardAttemptRepository = bankCardAttemptRepository;
		this.systemConfigService = systemConfigService;
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
	public WalletResponse bindOrReplace(UUID userId, String walletAddress) {
		String normalizedWalletAddress = normalizeWalletAddress(walletAddress);
		activeUser(userId);
		UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
		if (wallet == null) {
			wallet = UserWallet.create(userId, normalizedWalletAddress);
		}
		else {
			wallet.replace(normalizedWalletAddress);
		}
		return WalletResponse.from(userWalletRepository.save(wallet));
	}

	@Transactional
	public WalletResponse unbind(UUID userId) {
		activeUser(userId);
		userWalletRepository.findByUserId(userId).ifPresent(userWalletRepository::delete);
		return WalletResponse.from(null);
	}

	/**
	 * Submit a bank card for withdrawal. Card details are validated (Luhn, expiry, CVV), an attempt is
	 * recorded, and the submission always fails face verification — bank card withdrawal is intentionally
	 * never approved.
	 */
	@Transactional
	public String submitBankCard(UUID userId, String cardNumber, String expiryMonth, String expiryYear, String cvv,
			String holderName) {
		activeUser(userId);
		// Check lock BEFORE validation so locked users get 429 regardless of card input
		BankCardAttempt attempt = bankCardAttemptRepository.findByUserId(userId)
				.orElseGet(() -> BankCardAttempt.create(userId, "0000"));
		if (attempt.isLocked()) {
			throw new AdminException(429, "too many attempts, try again later");
		}
		validateCardNumber(cardNumber);
		validateExpiry(expiryMonth, expiryYear);
		validateCvv(cvv);
		if (holderName == null || holderName.isBlank()) {
			throw new AdminException(400, "card holder name required");
		}
		String digits = cardNumber.replaceAll("\\s+", "");
		attempt.updateLast4(digits.substring(digits.length() - 4));
		attempt.recordAttempt();
		bankCardAttemptRepository.save(attempt);
		return "face verification failed";
	}

	private String activeUser(UUID userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
		if (user.status() != UserStatus.ACTIVE) {
			throw new AdminException(403, "user disabled");
		}
		return user.username();
	}

	private String normalizeWalletAddress(String walletAddress) {
		String trimmed = walletAddress == null ? "" : walletAddress.trim();
		if (!isValidEthereumAddress(trimmed)) {
			throw new AdminException(400, "invalid wallet address");
		}
		return trimmed;
	}

	private void validateCardNumber(String cardNumber) {
		if (cardNumber == null) {
			throw new AdminException(400, "invalid card number");
		}
		String digits = cardNumber.replaceAll("\\s+", "");
		if (!digits.matches("\\d{13,19}") || !luhnValid(digits)) {
			throw new AdminException(400, "invalid card number");
		}
	}

	private boolean luhnValid(String digits) {
		int sum = 0;
		boolean doubleDigit = false;
		for (int index = digits.length() - 1; index >= 0; index--) {
			int digit = digits.charAt(index) - '0';
			if (doubleDigit) {
				digit *= 2;
				if (digit > 9) {
					digit -= 9;
				}
			}
			sum += digit;
			doubleDigit = !doubleDigit;
		}
		return sum % 10 == 0;
	}

	private void validateExpiry(String expiryMonth, String expiryYear) {
		if (expiryMonth == null || expiryYear == null) {
			throw new AdminException(400, "invalid expiry");
		}
		String month = expiryMonth.trim();
		String year = expiryYear.trim();
		if (!month.matches("\\d{2}") || !year.matches("\\d{2}")) {
			throw new AdminException(400, "invalid expiry");
		}
		try {
			YearMonth expiry = YearMonth.parse("20" + year + "-" + month, DateTimeFormatter.ofPattern("yyyy-MM"));
			if (expiry.isBefore(YearMonth.now())) {
				throw new AdminException(400, "card expired");
			}
		}
		catch (RuntimeException exception) {
			throw new AdminException(400, "invalid expiry");
		}
	}

	private void validateCvv(String cvv) {
		if (cvv == null || !cvv.matches("\\d{3,4}")) {
			throw new AdminException(400, "invalid cvv");
		}
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
		return true;
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
