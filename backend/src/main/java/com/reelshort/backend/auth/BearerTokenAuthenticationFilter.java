package com.reelshort.backend.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

	public static final String AUTH_FAILURE_ATTRIBUTE = "authFailure";

	private final AccessTokenRepository accessTokenRepository;
	private final TokenHasher tokenHasher;

	public BearerTokenAuthenticationFilter(AccessTokenRepository accessTokenRepository, TokenHasher tokenHasher) {
		this.accessTokenRepository = accessTokenRepository;
		this.tokenHasher = tokenHasher;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization != null && authorization.startsWith("Bearer ")) {
			authenticateToken(request, authorization.substring(7));
		}
		filterChain.doFilter(request, response);
	}

	private void authenticateToken(HttpServletRequest request, String token) {
		accessTokenRepository.findByTokenHash(tokenHasher.hash(token))
				.ifPresentOrElse(accessToken -> authenticate(request, accessToken.user()),
						() -> request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "invalid token"));
	}

	private void authenticate(HttpServletRequest request, UserAccount user) {
		if (user.status() == UserStatus.DISABLED) {
			request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "user disabled");
			return;
		}
		AppUserPrincipal principal = new AppUserPrincipal(user.id(), user.username(), user.status());
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				principal, null, List.of());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
