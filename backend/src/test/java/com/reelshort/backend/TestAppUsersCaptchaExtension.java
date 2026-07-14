package com.reelshort.backend;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.reelshort.backend.auth.CaptchaChallengeRepository;

/**
 * Auto-discovered JUnit extension that binds the Spring-managed {@link CaptchaChallengeRepository} into the
 * static {@link TestAppUsers} helper before any test class runs, so that username+password registration (which
 * requires captcha verification) works transparently in tests. Silently skips contexts where the repository
 * bean is not available (e.g. sliced tests without JPA).
 */
public class TestAppUsersCaptchaExtension implements BeforeAllCallback {

	@Override
	public void beforeAll(ExtensionContext context) {
		ApplicationContext applicationContext;
		try {
			applicationContext = SpringExtension.getApplicationContext(context);
		}
		catch (Exception exception) {
			// Context not managed by SpringExtension (e.g. pure unit test); nothing to bind.
			return;
		}
		try {
			TestAppUsers.bindCaptchaRepository(applicationContext.getBean(CaptchaChallengeRepository.class));
		}
		catch (NoSuchBeanDefinitionException exception) {
			// Sliced test context without JPA repositories; captcha registration not supported here.
		}
	}
}
