package com.reelshort.backend.content.account;

import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.system.config.SystemConfig;
import com.reelshort.backend.system.config.SystemConfigRepository;

/**
 * ReelShort 上游账号的维护与下发。
 *
 * <p>账号存进 {@code system_configs}（不走 {@link com.reelshort.backend.system.config.SystemConfigRegistry}，
 * 所以不会出现在通用配置表里、也不会被通用接口回显），保存后立刻推给 content-provider，
 * 由内容源拿去登录；content-provider 重启时后端启动完成会再推一次。
 *
 * <p>密码是"需要能还原"的凭据，因此不能哈希：库里存原值、接口永不回显，展示只给脱敏邮箱。
 */
@Service
public class ReelshortAccountService {

	public static final String EMAIL_KEY = "content.reelshort.email";
	public static final String PASSWORD_KEY = "content.reelshort.password";

	private static final Logger log = LoggerFactory.getLogger(ReelshortAccountService.class);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final int EMAIL_MAX_LENGTH = 190;
	private static final int PASSWORD_MIN_LENGTH = 6;
	private static final int PASSWORD_MAX_LENGTH = 64;
	private static final String EMAIL_DESCRIPTION = "ReelShort 上游账号邮箱（后台可改，密码不回显）";
	private static final String PASSWORD_DESCRIPTION = "ReelShort 上游账号密码（后台可改，接口不回显）";

	private final SystemConfigRepository systemConfigRepository;
	private final ObjectProvider<ReelshortAccountGateway> gatewayProvider;
	private final TransactionTemplate transactionTemplate;

	public ReelshortAccountService(SystemConfigRepository systemConfigRepository,
			ObjectProvider<ReelshortAccountGateway> gatewayProvider, PlatformTransactionManager transactionManager) {
		this.systemConfigRepository = systemConfigRepository;
		this.gatewayProvider = gatewayProvider;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * 库里存的账号 + 内容源实时状态。不包事务：这里有一次 HTTP 调用，
	 * 不该让它占着数据库连接。
	 */
	public ReelshortAccountView view() {
		ReelshortAccountView stored = storedAccount();
		if (!stored.configured() || !stored.passwordSet()) {
			return stored;
		}
		Optional<ReelshortAccountGateway.LiveStatus> live = liveStatus();
		if (live.isEmpty()) {
			return stored;
		}
		ReelshortAccountGateway.LiveStatus status = live.get();
		boolean known = status.vipKnown();
		return new ReelshortAccountView(stored.emailMasked(), stored.configured(), stored.passwordSet(),
				stored.updatedAt(), true, status.authenticated(), known, known ? status.vipActive() : null,
				known ? status.vipExpireAt() : null, known ? status.vipRenew() : null, known ? status.coins() : null);
	}

	private ReelshortAccountView storedAccount() {
		String email = storedValue(EMAIL_KEY);
		String password = storedValue(PASSWORD_KEY);
		if (email.isBlank() && password.isBlank()) {
			return ReelshortAccountView.empty();
		}
		String updatedAt = systemConfigRepository.findById(EMAIL_KEY).map(SystemConfig::updatedAt)
				.map(Object::toString)
				.orElse(null);
		return new ReelshortAccountView(maskEmail(email), !email.isBlank(), !password.isBlank(), updatedAt, false, false,
				false, null, null, null, null);
	}

	/** 向内容源要一次实时状态；没配齐账号或内容源不可达时返回空（后台显示"未知"）。 */
	private Optional<ReelshortAccountGateway.LiveStatus> liveStatus() {
		ReelshortAccountGateway gateway = gatewayProvider.getIfAvailable();
		if (gateway == null) {
			return Optional.empty();
		}
		try {
			return gateway.liveStatus();
		}
		catch (RuntimeException exception) {
			log.warn("ReelShort account live status failed: {}", exception.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * 保存账号并下发。密码留空表示"沿用已存的密码"——后台表单不回显密码，
	 * 只改邮箱时不需要重新输入。
	 *
	 * <p>先在一个短事务里落库，再在事务外下发：登录是一次上游 HTTP 调用，
	 * 不能让它把数据库连接一直占着。
	 */
	public ReelshortAccountSync save(String email, String password) {
		String normalizedEmail = validateEmail(email);
		String effectivePassword;
		if (password == null || password.isBlank()) {
			String stored = storedValue(PASSWORD_KEY);
			if (stored.isBlank()) {
				throw new AdminException(400, "password is required");
			}
			effectivePassword = stored;
		}
		else {
			effectivePassword = validatePassword(password);
		}
		transactionTemplate.executeWithoutResult((status) -> {
			upsert(EMAIL_KEY, normalizedEmail, EMAIL_DESCRIPTION);
			upsert(PASSWORD_KEY, effectivePassword, PASSWORD_DESCRIPTION);
		});
		return push(normalizedEmail, effectivePassword);
	}

	/** 用已保存的账号重新下发一次（后台"测试登录"按钮）。 */
	public ReelshortAccountSync resync() {
		String email = storedValue(EMAIL_KEY);
		String password = storedValue(PASSWORD_KEY);
		if (email.isBlank() || password.isBlank()) {
			throw new AdminException(400, "account is not configured");
		}
		return push(email, password);
	}

	/**
	 * 后端启动完成后把账号推给内容源：content-provider 重启会丢掉进程内的登录态，
	 * 而它的环境变量里可能已经没有这套凭据了。
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void pushOnStartup() {
		String email = storedValue(EMAIL_KEY);
		String password = storedValue(PASSWORD_KEY);
		if (email.isBlank() || password.isBlank()) {
			return;
		}
		try {
			ReelshortAccountSync result = push(email, password);
			if (!result.authenticated()) {
				log.warn("ReelShort account push on startup did not authenticate: {}", result.message());
			}
		}
		catch (RuntimeException exception) {
			log.warn("ReelShort account push on startup failed: {}", exception.getMessage());
		}
	}

	private ReelshortAccountSync push(String email, String password) {
		String masked = maskEmail(email);
		ReelshortAccountGateway gateway = gatewayProvider.getIfAvailable();
		if (gateway == null) {
			return new ReelshortAccountSync(false, masked, "已保存；当前未启用内容源账号下发");
		}
		try {
			ReelshortAccountGateway.ReelshortAccountPushResult result = gateway.updateAccount(email, password);
			return new ReelshortAccountSync(result.authenticated(), masked,
					result.authenticated() ? "已保存，内容源登录成功" : "已保存，内容源登录未成功（密码错误或账号未验证）");
		}
		catch (RuntimeException exception) {
			log.warn("ReelShort account push failed", exception);
			return new ReelshortAccountSync(false, masked, "已保存，但内容源暂不可达，稍后可点「测试登录」重试");
		}
	}

	private void upsert(String key, String value, String description) {
		SystemConfig config = systemConfigRepository.findById(key)
				.orElseGet(() -> SystemConfig.create(key, value, description));
		config.update(value, description);
		systemConfigRepository.save(config);
	}

	private String storedValue(String key) {
		return systemConfigRepository.findById(key).map(SystemConfig::value).orElse("");
	}

	private static String validateEmail(String email) {
		String normalized = email == null ? "" : email.trim();
		if (normalized.isEmpty() || normalized.length() > EMAIL_MAX_LENGTH
				|| !EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new AdminException(400, "invalid email");
		}
		return normalized;
	}

	private static String validatePassword(String password) {
		if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
			throw new AdminException(400, "password length must be between 6 and 64");
		}
		return password;
	}

	/** `operator@example.com` → `op***@example.com`；域名不变，够确认改的是哪个账号。 */
	static String maskEmail(String email) {
		if (email == null || email.isBlank()) {
			return "";
		}
		int at = email.indexOf('@');
		if (at < 1) {
			return email.substring(0, Math.min(2, email.length())) + "***";
		}
		String local = email.substring(0, at);
		String prefix = local.length() > 2 ? local.substring(0, 2) : local.substring(0, 1);
		return prefix + "***@" + email.substring(at + 1);
	}
}
