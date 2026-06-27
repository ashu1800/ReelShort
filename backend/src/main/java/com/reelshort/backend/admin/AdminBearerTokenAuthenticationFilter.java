package com.reelshort.backend.admin;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.reelshort.backend.auth.TokenHasher;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminBearerTokenAuthenticationFilter extends OncePerRequestFilter {

	public static final String AUTH_FAILURE_ATTRIBUTE = "adminAuthFailure";

	private final AdminTokenRepository adminTokenRepository;
	private final AdminUserRepository adminUserRepository;
	private final TokenHasher tokenHasher;

	public AdminBearerTokenAuthenticationFilter(AdminTokenRepository adminTokenRepository,
			AdminUserRepository adminUserRepository, TokenHasher tokenHasher) {
		this.adminTokenRepository = adminTokenRepository;
		this.adminUserRepository = adminUserRepository;
		this.tokenHasher = tokenHasher;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/admin/");
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
		adminTokenRepository.findByTokenHash(tokenHasher.hash(token))
				.ifPresentOrElse(adminToken -> authenticateOrReject(request, adminToken),
						() -> request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "invalid token"));
	}

	private void authenticateOrReject(HttpServletRequest request, AdminToken adminToken) {
		if (adminToken.isRevoked()) {
			request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "token revoked");
			return;
		}
		if (adminToken.isExpired(OffsetDateTime.now())) {
			request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "token expired");
			return;
		}
		if (adminToken.adminUserId() == null) {
			request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "legacy token");
			return;
		}
		adminUserRepository.findWithRolesById(adminToken.adminUserId())
				.ifPresentOrElse(admin -> authenticateAdmin(request, admin),
						() -> request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "admin missing"));
	}

	private void authenticateAdmin(HttpServletRequest request, AdminUser admin) {
		if (admin.status() != AdminUserStatus.ACTIVE) {
			request.setAttribute(AUTH_FAILURE_ATTRIBUTE, "admin disabled");
			return;
		}
		AdminPrincipal principal = new AdminPrincipal(admin.id(), admin.username(), admin.permissionCodes());
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				principal, null, List.of());
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
