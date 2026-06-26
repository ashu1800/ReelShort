package com.reelshort.backend.system.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.reelshort.backend.admin.AdminPrincipal;
import com.reelshort.backend.auth.AppUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RateLimitKeyResolver {

	private static final Pattern IPV4_PATTERN = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

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

	private boolean isTrustedProxyAddress(String remoteAddress) {
		return parseIpAddress(remoteAddress)
				.map(address -> address.isLoopbackAddress() || address.isSiteLocalAddress()
						|| address.isLinkLocalAddress())
				.orElse(false);
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
