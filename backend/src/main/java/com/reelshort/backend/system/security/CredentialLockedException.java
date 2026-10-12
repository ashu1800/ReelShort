package com.reelshort.backend.system.security;

/**
 * P1-5: 凭证在锁定窗口内被拒绝。message 固定，不泄露剩余次数或锁定到期时间。
 */
public class CredentialLockedException extends RuntimeException {

	private static final String MESSAGE = "too many attempts, try again later";

	public CredentialLockedException() {
		super(MESSAGE);
	}
}
