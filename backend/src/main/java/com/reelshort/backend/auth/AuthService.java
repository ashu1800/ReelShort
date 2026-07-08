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
	private final PhoneNumberNormalizer phoneNumberNormalizer;
	private final SmsVerificationService smsVerificationService;

	public AuthService(UserAccountRepository userAccountRepository, PasswordHasher passwordHasher,
			TokenService tokenService, PhoneNumberNormalizer phoneNumberNormalizer,
			SmsVerificationService smsVerificationService) {
		this.userAccountRepository = userAccountRepository;
		this.passwordHasher = passwordHasher;
		this.tokenService = tokenService;
		this.phoneNumberNormalizer = phoneNumberNormalizer;
		this.smsVerificationService = smsVerificationService;
	}

	@Transactional
	public RegisterSimulationResponse register(String countryCode, String phoneNumber, String password,
			String verificationCode) {
		PhoneIdentity phone = phoneNumberNormalizer.normalize(countryCode, phoneNumber);
		smsVerificationService.verifyAndConsume(SmsVerificationPurpose.PUBLIC_REGISTER, phone, verificationCode);
		return new RegisterSimulationResponse("SIMULATED");
	}

	@Transactional
	public AuthToken login(String countryCode, String phoneNumber, String password) {
		PhoneIdentity phone = phoneNumberNormalizer.normalize(countryCode, phoneNumber);
		UserAccount user = userAccountRepository.findByPhoneE164(phone.e164())
				.orElseThrow(() -> new AuthException(401, "invalid phone or password"));
		if (user.status() != UserStatus.ACTIVE) {
			throw new AuthException(403, "user disabled");
		}
		if (!passwordHasher.matches(password, user.passwordHash())) {
			throw new AuthException(401, "invalid phone or password");
		}
		return tokenService.issue(user);
	}

	@Transactional
	public AuthToken internalRegisterPhone(String countryCode, String phoneNumber, String password) {
		PhoneIdentity phone = phoneNumberNormalizer.normalize(countryCode, phoneNumber);
		if (userAccountRepository.existsByPhoneE164(phone.e164())) {
			throw new AuthException(409, "phone already exists");
		}
		UserAccount user = UserAccount.createPhoneAccount(phone.countryCode(), phone.phoneNumber(), phone.e164(),
				passwordHasher.hash(password));
		try {
			return tokenService.issue(userAccountRepository.save(user));
		}
		catch (DataIntegrityViolationException exception) {
			throw new AuthException(409, "phone already exists");
		}
	}

	@Transactional
	public SmsSendResponse sendSms(SmsVerificationPurpose purpose, String countryCode, String phoneNumber) {
		if (purpose != SmsVerificationPurpose.PUBLIC_REGISTER) {
			throw new AuthException(400, "sms purpose not allowed");
		}
		PhoneIdentity phone = phoneNumberNormalizer.normalize(countryCode, phoneNumber);
		return smsVerificationService.send(purpose, phone);
	}

	@Transactional
	public SmsSendResponse sendPasswordChangeVerification(CurrentUser currentUser) {
		UserAccount user = activePhoneUser(currentUser);
		PhoneIdentity phone = phoneNumberNormalizer.normalize(user.phoneCountryCode(), user.phoneNumber());
		return smsVerificationService.send(SmsVerificationPurpose.PASSWORD_CHANGE, phone);
	}

	@Transactional
	public void changePassword(CurrentUser currentUser, String oldPassword, String newPassword, String verificationCode) {
		UserAccount user = activePhoneUser(currentUser);
		if (!passwordHasher.matches(oldPassword, user.passwordHash())) {
			throw new AuthException(401, "invalid phone or password");
		}
		PhoneIdentity phone = phoneNumberNormalizer.normalize(user.phoneCountryCode(), user.phoneNumber());
		smsVerificationService.verifyAndConsume(SmsVerificationPurpose.PASSWORD_CHANGE, phone, verificationCode);
		user.changePasswordHash(passwordHasher.hash(newPassword));
		userAccountRepository.save(user);
		tokenService.revokeAllForUser(user.id());
	}

	@Transactional
	public void logout(String token) {
		tokenService.revoke(token);
	}

	private UserAccount activePhoneUser(CurrentUser currentUser) {
		UserAccount user = userAccountRepository.findById(currentUser.userId())
				.orElseThrow(() -> new AuthException(401, "unauthorized"));
		if (user.status() != UserStatus.ACTIVE) {
			throw new AuthException(403, "user disabled");
		}
		if (user.phoneCountryCode() == null || user.phoneNumber() == null) {
			throw new AuthException(400, "phone account required");
		}
		return user;
	}

}
