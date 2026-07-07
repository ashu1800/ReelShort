package com.reelshort.backend.wallet;

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
		if (!trimmed.startsWith("T") || trimmed.length() < 30 || trimmed.length() > 64) {
			throw new AdminException(400, "invalid wallet address");
		}
		return trimmed;
	}
}
