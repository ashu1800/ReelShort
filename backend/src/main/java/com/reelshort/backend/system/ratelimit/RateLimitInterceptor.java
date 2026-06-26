package com.reelshort.backend.system.ratelimit;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.system.api.ApiErrorResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@ConditionalOnBean(RateLimitStore.class)
public class RateLimitInterceptor implements HandlerInterceptor {

	private final RateLimitProperties properties;
	private final RateLimitStore rateLimitStore;
	private final RateLimitKeyResolver rateLimitKeyResolver;
	private final ObjectMapper objectMapper;

	public RateLimitInterceptor(RateLimitProperties properties, RateLimitStore rateLimitStore,
			RateLimitKeyResolver rateLimitKeyResolver, ObjectMapper objectMapper) {
		this.properties = properties;
		this.rateLimitStore = rateLimitStore;
		this.rateLimitKeyResolver = rateLimitKeyResolver;
		this.objectMapper = objectMapper;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (!properties.isEnabled()) {
			return true;
		}
		Optional<RateLimitRule> rule = matchingRule(request.getMethod(), request.getRequestURI());
		if (rule.isEmpty()) {
			return true;
		}
		RateLimitRule rateLimitRule = rule.get();
		String key = rateLimitKeyResolver.resolveKey(rateLimitRule.name(), request);
		RateLimitResult result = rateLimitStore.tryAcquire(key, rateLimitRule.limit(), rateLimitRule.window());
		if (result.allowed()) {
			return true;
		}
		writeRateLimitError(request, response, result);
		return false;
	}

	private Optional<RateLimitRule> matchingRule(String method, String path) {
		List<RateLimitRule> rules = properties.toRules();
		return rules.stream()
				.filter(rule -> rule.matches(method, path))
				.findFirst();
	}

	private void writeRateLimitError(HttpServletRequest request, HttpServletResponse response, RateLimitResult result)
			throws IOException {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType("application/json");
		response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
		objectMapper.writeValue(response.getWriter(),
				ApiErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS.value(), "too many requests",
						request.getRequestURI(), requestId));
	}
}
