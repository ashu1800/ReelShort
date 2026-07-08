package com.reelshort.backend.wallet;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.auth.PhoneIdentity;
import com.reelshort.backend.auth.PhoneNumberNormalizer;
import com.reelshort.backend.auth.SmsSendResponse;
import com.reelshort.backend.auth.SmsVerificationPurpose;
import com.reelshort.backend.auth.SmsVerificationService;
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
	private final PhoneNumberNormalizer phoneNumberNormalizer;
	private final SmsVerificationService smsVerificationService;

	public WalletService(UserWalletRepository userWalletRepository, UserAccountRepository userAccountRepository,
			PhoneNumberNormalizer phoneNumberNormalizer, SmsVerificationService smsVerificationService) {
		this.userWalletRepository = userWalletRepository;
		this.userAccountRepository = userAccountRepository;
		this.phoneNumberNormalizer = phoneNumberNormalizer;
		this.smsVerificationService = smsVerificationService;
	}

	@Transactional(readOnly = true)
	public WalletResponse wallet(UUID userId) {
		return WalletResponse.from(userWalletRepository.findByUserId(userId).orElse(null));
	}

	@Transactional
	public SmsSendResponse sendVerification(UUID userId, SmsVerificationPurpose purpose) {
		if (purpose != SmsVerificationPurpose.WALLET_BIND
				&& purpose != SmsVerificationPurpose.WALLET_REPLACE
				&& purpose != SmsVerificationPurpose.WALLET_UNBIND) {
			throw new AdminException(400, "bad request");
		}
		return smsVerificationService.send(purpose, phone(userId));
	}

	@Transactional
	public WalletResponse bindOrReplace(UUID userId, String walletAddress, String verificationCode) {
		String normalizedWalletAddress = normalizeWalletAddress(walletAddress);
		PhoneIdentity phone = phone(userId);
		UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
		SmsVerificationPurpose purpose = wallet == null ? SmsVerificationPurpose.WALLET_BIND
				: SmsVerificationPurpose.WALLET_REPLACE;
		smsVerificationService.verifyAndConsume(purpose, phone, verificationCode);
		if (wallet == null) {
			wallet = UserWallet.create(userId, normalizedWalletAddress);
		}
		else {
			wallet.replace(normalizedWalletAddress);
		}
		return WalletResponse.from(userWalletRepository.save(wallet));
	}

	@Transactional
	public WalletResponse unbind(UUID userId, String verificationCode) {
		PhoneIdentity phone = phone(userId);
		smsVerificationService.verifyAndConsume(SmsVerificationPurpose.WALLET_UNBIND, phone, verificationCode);
		userWalletRepository.findByUserId(userId).ifPresent(userWalletRepository::delete);
		return new WalletResponse("TRC20", null, null);
	}

	public void rejectBankCard() {
		throw new AdminException(400, "Bank card withdrawal is not supported");
	}

	private PhoneIdentity phone(UUID userId) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AdminException(404, "user not found"));
		if (user.status() != UserStatus.ACTIVE) {
			throw new AdminException(403, "user disabled");
		}
		if (user.phoneCountryCode() == null || user.phoneNumber() == null) {
			throw new AdminException(400, "phone account required");
		}
		return phoneNumberNormalizer.normalize(user.phoneCountryCode(), user.phoneNumber());
	}

	private String normalizeWalletAddress(String walletAddress) {
		String trimmed = walletAddress == null ? "" : walletAddress.trim();
		if (!isValidTronAddress(trimmed)) {
			throw new AdminException(400, "invalid wallet address");
		}
		return trimmed;
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
}
