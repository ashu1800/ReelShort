package com.reelshort.backend.system.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * P1-5: 凭证（改密 / 钱包改绑密码）失败锁定配置。
 */
@ConfigurationProperties(prefix = "reelshort.security.credential-guard")
public record CredentialGuardProperties(int maxFailures, Duration lockDuration) {

	public CredentialGuardProperties {
		// 配置缺失或明显误配（0/负数）时回落到安全默认值：5 次失败锁定 15 分钟。
		if (maxFailures <= 0) {
			maxFailures = 5;
		}
		if (lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()) {
			lockDuration = Duration.ofMinutes(15);
		}
	}
}
