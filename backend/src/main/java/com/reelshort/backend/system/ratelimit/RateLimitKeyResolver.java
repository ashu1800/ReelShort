package com.reelshort.backend.system.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.reelshort.backend.admin.AdminPrincipal;
import com.reelshort.backend.auth.AppUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RateLimitKeyResolver {

	private static final Pattern IPV4_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

	// H3: 不再按"是否内网"判断可信代理（Docker 默认网段 172.x 导致任何内网请求可伪造 X-Forwarded-For）。
	// 改为显式可配置的代理 IP/网段白名单，默认仅信任 loopback。
	private final Set<String> trustedProxies;

	public RateLimitKeyResolver(@Value("${reelshort.rate-limit.trusted-proxies:}") String trustedProxies) {
		this.trustedProxies = trustedProxies == null || trustedProxies.isBlank()
				? Collections.emptySet()
				: new HashSet<>(Arrays.asList(trustedProxies.split("\\s*,\\s*")));
	}

	public String resolveKey(String ruleName, HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null) {
			Object principal = authentication.getPrincipal();
			if (principal instanceof AppUserPrincipal appUserPrincipal) {
				return ruleName + ":APP:" + appUserPrincipal.userId();
			}
			if (principal instanceof AdminPrincipal adminPrincipal) {
				return ruleName + ":ADMIN:" + adminPrincipal.username();
			}
		}
		return ruleName + ":IP:" + clientIp(request);
	}

	private String clientIp(HttpServletRequest request) {
		String remoteAddress = request.getRemoteAddr();
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank() && isTrustedProxyAddress(remoteAddress)) {
			return forwardedClientIp(forwardedFor).orElse(remoteAddress);
		}
		return remoteAddress;
	}

	private Optional<String> forwardedClientIp(String forwardedFor) {
		String fallback = null;
		String[] addresses = forwardedFor.split(",");
		for (int i = addresses.length - 1; i >= 0; i--) {
			String candidate = addresses[i].trim();
			if (parseIpAddress(candidate).isEmpty()) {
				continue;
			}
			if (fallback == null) {
				fallback = candidate;
			}
			if (!isTrustedProxyAddress(candidate)) {
				return Optional.of(candidate);
			}
		}
		return Optional.ofNullable(fallback);
	}

	/**
	 * H3: 只有显式配置在 trusted-proxies 白名单中的 IP 才被当作可信代理。
	 * 不再使用 isSiteLocalAddress 等宽泛判断（Docker 网段 172.x 被误判为可信）。
	 * loopback（127.0.0.1/::1）始终可信，因为只有同机 Nginx 才从 loopback 转发。
	 */
	private boolean isTrustedProxyAddress(String remoteAddress) {
		if (remoteAddress == null || remoteAddress.isBlank()) {
			return false;
		}
		String trimmed = remoteAddress.trim();
		// loopback 始终可信（同机 Nginx 反代场景）
		if ("127.0.0.1".equals(trimmed) || "::1".equals(trimmed) || "0:0:0:0:0:0:0:1".equals(trimmed)) {
			return true;
		}
		return trustedProxies.contains(trimmed);
	}

	private Optional<InetAddress> parseIpAddress(String remoteAddress) {
		if (remoteAddress == null || remoteAddress.isBlank()) {
			return Optional.empty();
		}
		String candidate = remoteAddress.trim();
		if (candidate.startsWith("[") && candidate.endsWith("]")) {
			candidate = candidate.substring(1, candidate.length() - 1);
		}
		if (!candidate.contains(":") && !IPV4_PATTERN.matcher(candidate).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(InetAddress.getByName(candidate));
		}
		catch (UnknownHostException exception) {
			return Optional.empty();
		}
	}
}
