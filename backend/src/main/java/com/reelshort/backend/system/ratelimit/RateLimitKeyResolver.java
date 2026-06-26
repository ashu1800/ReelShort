package com.reelshort.backend.system.ratelimit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.reelshort.backend.admin.AdminPrincipal;
import com.reelshort.backend.auth.AppUserPrincipal;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RateLimitKeyResolver {

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
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
