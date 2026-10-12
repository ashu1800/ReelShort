package com.reelshort.backend.system.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.reelshort.backend.admin.AdminPrincipal;
import com.reelshort.backend.auth.AppUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RateLimitKeyResolver {

	private static final Logger log = LoggerFactory.getLogger(RateLimitKeyResolver.class);

	private static final Pattern IPV4_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

	// P1-6: 生产 Docker 场景下 backend 看到的 remoteAddr 是网关地址（非 loopback），
	// 因此白名单必须支持网段。0.0.0.0/0 之类的过宽前缀会让任意客户端伪造 X-Forwarded-For，
	// 因此前缀小于以下阈值时直接启动失败（fail-closed），而不是静默地把全部来源当成可信代理。
	private static final int IPV4_MIN_PREFIX = 8;
	private static final int IPV6_MIN_PREFIX = 16;

	// H3: 不再按"是否内网"判断可信代理（Docker 默认网段 172.x 导致任何内网请求可伪造 X-Forwarded-For）。
	// 改为显式可配置的代理 IP/网段白名单，默认仅信任 loopback。
	private final List<TrustedProxy> trustedProxies;

	// P1-6: 只有第一次出现"带 XFF 但来源不可信"时打 WARN，避免每个请求刷日志。
	private final AtomicBoolean untrustedForwardedForWarned = new AtomicBoolean();

	public RateLimitKeyResolver(@Value("${reelshort.rate-limit.trusted-proxies:}") String trustedProxies) {
		this.trustedProxies = parseTrustedProxies(trustedProxies);
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
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			if (isTrustedProxyAddress(remoteAddress)) {
				return forwardedClientIp(forwardedFor).orElse(remoteAddress);
			}
			warnUntrustedForwardedForOnce(remoteAddress);
		}
		return remoteAddress;
	}

	/**
	 * P1-6: 白名单未覆盖实际代理地址时，X-Forwarded-For 会被整体忽略，
	 * 全部匿名客户端退化为同一个限流键（全站共享限流桶）。这里只提示一次事实与修复方式，
	 * 不打印完整 X-Forwarded-For（可能包含用户真实 IP）。
	 */
	private void warnUntrustedForwardedForOnce(String remoteAddress) {
		if (!untrustedForwardedForWarned.compareAndSet(false, true)) {
			return;
		}
		log.warn("X-Forwarded-For header received but remote address {} is not a trusted proxy; "
				+ "forwarded client addresses are ignored, rate limiting counts every anonymous client against a "
				+ "single shared source key and one client can throttle all clients. Configure "
				+ "reelshort.rate-limit.trusted-proxies with the proxy IP or CIDR.", remoteAddress);
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
	 * H3: 只有显式配置在 trusted-proxies 白名单中的 IP 或网段（CIDR）才被当作可信代理。
	 * 不再使用 isSiteLocalAddress 等宽泛判断（Docker 网段 172.x 被误判为可信）。
	 * loopback（127.0.0.0/8、::1）始终可信，因为只有同机 Nginx 才从 loopback 转发。
	 */
	private boolean isTrustedProxyAddress(String remoteAddress) {
		if (remoteAddress == null || remoteAddress.isBlank()) {
			return false;
		}
		String trimmed = remoteAddress.trim();
		Optional<InetAddress> parsed = parseIpAddress(trimmed);
		if (parsed.isPresent() && parsed.get().isLoopbackAddress()) {
			return true;
		}
		if (parsed.isEmpty()) {
			return false;
		}
		byte[] addressBytes = parsed.get().getAddress();
		for (TrustedProxy trustedProxy : trustedProxies) {
			if (trustedProxy.matches(addressBytes)) {
				return true;
			}
		}
		return false;
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

	/**
	 * 解析逗号分隔的可信代理白名单，支持 {@code IPv4}、{@code IPv4/prefix}、{@code IPv6}、{@code IPv6/prefix}。
	 * 无法解析的条目只告警并跳过（保持"配置写错也不会阻断启动"的既有行为），
	 * 但前缀过宽属于打开伪造入口的危险误配，直接抛 {@link IllegalStateException} 让启动失败。
	 */
	private List<TrustedProxy> parseTrustedProxies(String trustedProxies) {
		if (trustedProxies == null || trustedProxies.isBlank()) {
			return Collections.emptyList();
		}
		List<TrustedProxy> proxies = new ArrayList<>();
		for (String entry : trustedProxies.split("\\s*,\\s*")) {
			if (entry.isBlank()) {
				continue;
			}
			TrustedProxy trustedProxy = parseTrustedProxy(entry.trim());
			if (trustedProxy != null) {
				proxies.add(trustedProxy);
			}
		}
		return List.copyOf(proxies);
	}

	private TrustedProxy parseTrustedProxy(String entry) {
		String addressPart = entry;
		String prefixPart = null;
		int separator = entry.indexOf('/');
		if (separator >= 0) {
			addressPart = entry.substring(0, separator);
			prefixPart = entry.substring(separator + 1);
		}
		Optional<InetAddress> parsed = parseIpAddress(addressPart);
		if (parsed.isEmpty()) {
			log.warn("Ignoring unparseable trusted proxy entry in reelshort.rate-limit.trusted-proxies: {}", entry);
			return null;
		}
		byte[] address = parsed.get().getAddress();
		int maximumPrefix = address.length * 8;
		if (prefixPart == null) {
			return new TrustedProxy(address, maximumPrefix);
		}
		int prefixLength;
		try {
			prefixLength = Integer.parseInt(prefixPart.trim());
		}
		catch (NumberFormatException exception) {
			throw new IllegalStateException("invalid trusted proxy prefix in reelshort.rate-limit.trusted-proxies: "
					+ entry);
		}
		if (prefixLength < 0 || prefixLength > maximumPrefix) {
			throw new IllegalStateException("invalid trusted proxy prefix in reelshort.rate-limit.trusted-proxies: "
					+ entry);
		}
		int minimumPrefix = address.length == 4 ? IPV4_MIN_PREFIX : IPV6_MIN_PREFIX;
		if (prefixLength < minimumPrefix) {
			throw new IllegalStateException("trusted proxy prefix is too broad in reelshort.rate-limit.trusted-proxies: "
					+ entry + " (minimum " + minimumPrefix + "), it would trust every client address");
		}
		return new TrustedProxy(address, prefixLength);
	}

	/**
	 * 可信代理网段条目。用 {@link InetAddress} 的字节前缀比较（不做字符串 startsWith，
	 * 避免 {@code 10.0.0.2} 命中 {@code 10.0.0.20} 这类前缀误判）。
	 */
	private record TrustedProxy(byte[] network, int prefixLength) {

		boolean matches(byte[] address) {
			if (address.length != network.length) {
				return false;
			}
			int fullBytes = prefixLength / 8;
			for (int index = 0; index < fullBytes; index++) {
				if (address[index] != network[index]) {
					return false;
				}
			}
			int remainingBits = prefixLength % 8;
			if (remainingBits == 0) {
				return true;
			}
			int mask = (0xFF << (8 - remainingBits)) & 0xFF;
			return (address[fullBytes] & mask) == (network[fullBytes] & mask);
		}
	}
}
