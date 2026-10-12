package com.reelshort.backend.system.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_ATTRIBUTE = "requestId";
	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	/**
	 * P3-12: 客户端传入的 X-Request-Id 直接写入日志/错误体/审计会被用来伪造日志与污染取证，
	 * 因此只接受受限字符集与长度（8-64 位），不合规一律重新生成 UUID。
	 */
	private static final Pattern ALLOWED_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (requestId == null || !ALLOWED_REQUEST_ID.matcher(requestId).matches()) {
			requestId = UUID.randomUUID().toString();
		}
		request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		filterChain.doFilter(request, response);
	}
}
