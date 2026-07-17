package com.reelshort.backend.system.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.reelshort.backend.admin.AdminPrincipal;
import com.reelshort.backend.auth.AppUserPrincipal;
import com.reelshort.backend.user.UserStatus;

class RateLimitRuleTests {

	@Test
	void exactRuleMatchesMethodAndPath() {
		RateLimitRule rule = new RateLimitRule("app-login", "POST", "/api/app/auth/login", 5,
				Duration.ofMinutes(1));

		assertThat(rule.matches("POST", "/api/app/auth/login")).isTrue();
		assertThat(rule.matches("GET", "/api/app/auth/login")).isFalse();
		assertThat(rule.matches("POST", "/api/app/auth/register")).isFalse();
	}

	@Test
	void wildcardRuleMatchesPathPrefix() {
		RateLimitRule rule = new RateLimitRule("app-content", "GET", "/api/app/content/**", 30,
				Duration.ofMinutes(1));

		assertThat(rule.matches("GET", "/api/app/content/search")).isTrue();
		assertThat(rule.matches("GET", "/api/app/content/books/book-1/episodes/1/play")).isTrue();
		assertThat(rule.matches("GET", "/api/app/points/account")).isFalse();
	}

	@Test
	void keyResolverUsesFirstForwardedIpWhenNoPrincipalExists() {
		// H3: trusted-proxies 白名单为空时仅信任 loopback；10.0.0.2 不再被自动信任
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("");
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/auth/login");
		request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.2");
		request.setRemoteAddr("127.0.0.1");

		String key = resolver.resolveKey("app-login", request);

		// 10.0.0.2 不在白名单 → 返回最近的不可信 IP（即 10.0.0.2）
		assertThat(key).isEqualTo("app-login:IP:10.0.0.2");
	}

	@Test
	void keyResolverIgnoresForwardedIpFromUntrustedRemoteAddress() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("");
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/auth/login");
		request.addHeader("X-Forwarded-For", "203.0.113.1");
		request.setRemoteAddr("198.51.100.50");

		String key = resolver.resolveKey("app-login", request);

		assertThat(key).isEqualTo("app-login:IP:198.51.100.50");
	}

	@Test
	void keyResolverUsesNearestUntrustedForwardedIpFromTrustedProxy() {
		// H3: 10.0.0.2 不再被自动信任；只有 loopback 可信。
		// 要让多跳 X-Forwarded-For 正确解析，需显式配置 10.0.0.2 为可信代理。
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("10.0.0.2");
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/auth/login");
		request.addHeader("X-Forwarded-For", "198.51.100.200, 203.0.113.1, 10.0.0.2");
		request.setRemoteAddr("127.0.0.1");

		String key = resolver.resolveKey("app-login", request);

		// 10.0.0.2 在白名单 → 跳过；203.0.113.1 不可信 → 返回它
		assertThat(key).isEqualTo("app-login:IP:203.0.113.1");
	}

	@Test
	void keyResolverUsesAppPrincipalBeforeIp() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/content/search");
		request.setRemoteAddr("127.0.0.1");
		AppUserPrincipal principal = new AppUserPrincipal(java.util.UUID.randomUUID(), "alice", UserStatus.ACTIVE);
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));

		String key = resolver.resolveKey("app-search", request);

		assertThat(key).isEqualTo("app-search:APP:" + principal.userId());
		SecurityContextHolder.clearContext();
	}

	@Test
	void keyResolverUsesAdminPrincipalBeforeIp() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
		request.setRemoteAddr("127.0.0.1");
		AdminPrincipal principal = new AdminPrincipal("admin");
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));

		String key = resolver.resolveKey("admin", request);

		assertThat(key).isEqualTo("admin:ADMIN:admin");
		SecurityContextHolder.clearContext();
	}
}
