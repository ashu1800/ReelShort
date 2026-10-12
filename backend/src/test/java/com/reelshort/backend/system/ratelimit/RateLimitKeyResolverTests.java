package com.reelshort.backend.system.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.reelshort.backend.auth.AppUserPrincipal;
import com.reelshort.backend.user.UserStatus;

/**
 * P1-6: 可信代理白名单支持 CIDR，且前缀过宽（0.0.0.0/0 之类）必须启动失败。
 */
class RateLimitKeyResolverTests {

	private MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/app/auth/login");
		request.setRemoteAddr(remoteAddr);
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor);
		}
		return request;
	}

	@Test
	void trustedIpv4CidrAcceptsDockerGatewayAddress() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("172.18.0.0/16");

		String key = resolver.resolveKey("app-login", request("172.18.0.5", "203.0.113.9"));

		assertThat(key).isEqualTo("app-login:IP:203.0.113.9");
	}

	@Test
	void trustedIpv4CidrDoesNotMatchNeighbouringRange() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("172.18.0.0/16");

		String key = resolver.resolveKey("app-login", request("172.19.0.5", "203.0.113.9"));

		assertThat(key).isEqualTo("app-login:IP:172.19.0.5");
	}

	@Test
	void cidrPrefixIsComparedPerBit() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("10.0.0.0/28");

		assertThat(resolver.resolveKey("app-login", request("10.0.0.15", "203.0.113.9")))
				.isEqualTo("app-login:IP:203.0.113.9");
		assertThat(resolver.resolveKey("app-login", request("10.0.0.16", "203.0.113.9")))
				.isEqualTo("app-login:IP:10.0.0.16");
	}

	@Test
	void exactIpEntryStillMatchesFullAddressOnly() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("10.0.0.2");

		assertThat(resolver.resolveKey("app-login", request("10.0.0.2", "203.0.113.9")))
				.isEqualTo("app-login:IP:203.0.113.9");
		assertThat(resolver.resolveKey("app-login", request("10.0.0.20", "203.0.113.9")))
				.isEqualTo("app-login:IP:10.0.0.20");
	}

	@Test
	void trustedIpv6CidrAcceptsAddressInsidePrefix() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("2001:db8:1234::/48");

		String key = resolver.resolveKey("app-login", request("2001:db8:1234:5678::1", "203.0.113.9"));

		assertThat(key).isEqualTo("app-login:IP:203.0.113.9");
	}

	@Test
	void trustedIpv6CidrDoesNotMatchAddressOutsidePrefix() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("2001:db8:1234::/48");

		String key = resolver.resolveKey("app-login", request("2001:db8:9999::1", "203.0.113.9"));

		assertThat(key).isEqualTo("app-login:IP:2001:db8:9999::1");
	}

	@Test
	void multiHopForwardedForSkipsTrustedProxies() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("172.18.0.0/16,10.0.0.2");

		String key = resolver.resolveKey("app-login",
				request("172.18.0.5", "198.51.100.200, 203.0.113.1, 10.0.0.2"));

		assertThat(key).isEqualTo("app-login:IP:203.0.113.1");
	}

	@Test
	void allForwardedAddressesTrustedFallsBackToNearestAddress() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("10.0.0.2");

		String key = resolver.resolveKey("app-login", request("127.0.0.1", "10.0.0.2"));

		assertThat(key).isEqualTo("app-login:IP:10.0.0.2");
	}

	@Test
	void emptyWhitelistTrustsOnlyLoopback() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("");

		// loopback 始终可信（同机 Nginx），XFF 被采纳
		assertThat(resolver.resolveKey("app-login", request("127.0.0.1", "203.0.113.9")))
				.isEqualTo("app-login:IP:203.0.113.9");
		// 非 loopback 来源一律忽略 XFF，按 remoteAddr 计算
		assertThat(resolver.resolveKey("app-login", request("198.51.100.50", "203.0.113.9")))
				.isEqualTo("app-login:IP:198.51.100.50");
	}

	@Test
	void blankWhitelistBehavesLikeEmptyWhitelist() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("   ");

		assertThat(resolver.resolveKey("app-login", request("198.51.100.50", "203.0.113.9")))
				.isEqualTo("app-login:IP:198.51.100.50");
	}

	@Test
	void ipv4PrefixBelowMinimumFailsStartup() {
		assertThatThrownBy(() -> new RateLimitKeyResolver("0.0.0.0/0"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("too broad");
		assertThatThrownBy(() -> new RateLimitKeyResolver("10.0.0.0/7"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("too broad");
	}

	@Test
	void ipv6PrefixBelowMinimumFailsStartup() {
		assertThatThrownBy(() -> new RateLimitKeyResolver("::/0"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("too broad");
		assertThatThrownBy(() -> new RateLimitKeyResolver("2001:db8::/15"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("too broad");
	}

	@Test
	void minimumAllowedPrefixesAreAccepted() {
		assertThatCode(() -> new RateLimitKeyResolver("10.0.0.0/8")).doesNotThrowAnyException();
		assertThatCode(() -> new RateLimitKeyResolver("2001:db8::/16")).doesNotThrowAnyException();
	}

	@Test
	void outOfRangePrefixFailsStartup() {
		assertThatThrownBy(() -> new RateLimitKeyResolver("10.0.0.0/33"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new RateLimitKeyResolver("2001:db8::/129"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new RateLimitKeyResolver("10.0.0.0/abc"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unparseableEntriesAreIgnoredWithoutFailingStartup() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("not-an-ip, 10.0.0.2");

		assertThat(resolver.resolveKey("app-login", request("10.0.0.2", "203.0.113.9")))
				.isEqualTo("app-login:IP:203.0.113.9");
	}

	@Test
	void principalTakesPrecedenceOverForwardedAddress() {
		RateLimitKeyResolver resolver = new RateLimitKeyResolver("");
		MockHttpServletRequest request = request("198.51.100.50", "203.0.113.9");
		AppUserPrincipal principal = new AppUserPrincipal(UUID.randomUUID(), "alice", UserStatus.ACTIVE);
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));

		try {
			assertThat(resolver.resolveKey("app-login", request)).isEqualTo("app-login:APP:" + principal.userId());
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}
}
