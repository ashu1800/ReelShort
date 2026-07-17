package com.reelshort.backend.withdrawal;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reelshort.withdrawal.payout")
public class WithdrawalPayoutProperties {

	private Duration signingLease = Duration.ofMinutes(1);
	private int unknownMaxAttempts = 5;
	private Duration unknownMaxAge = Duration.ofMinutes(15);

	public Duration getSigningLease() {
		return signingLease;
	}

	public void setSigningLease(Duration signingLease) {
		this.signingLease = signingLease;
	}

	public int getUnknownMaxAttempts() {
		return unknownMaxAttempts;
	}

	public void setUnknownMaxAttempts(int unknownMaxAttempts) {
		this.unknownMaxAttempts = unknownMaxAttempts;
	}

	public Duration getUnknownMaxAge() {
		return unknownMaxAge;
	}

	public void setUnknownMaxAge(Duration unknownMaxAge) {
		this.unknownMaxAge = unknownMaxAge;
	}
}
