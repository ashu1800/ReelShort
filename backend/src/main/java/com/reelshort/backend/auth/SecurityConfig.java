package com.reelshort.backend.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.admin.AdminBearerTokenAuthenticationFilter;
import com.reelshort.backend.admin.AdminProperties;
import com.reelshort.backend.admin.AdminSessionProperties;
import com.reelshort.backend.system.api.ApiErrorResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableConfigurationProperties({AdminProperties.class, AdminSessionProperties.class, AuthSessionProperties.class})
public class SecurityConfig {

	private final BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter;
	private final AdminBearerTokenAuthenticationFilter adminBearerTokenAuthenticationFilter;
	private final ObjectMapper objectMapper;

	public SecurityConfig(BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter,
			AdminBearerTokenAuthenticationFilter adminBearerTokenAuthenticationFilter, ObjectMapper objectMapper) {
		this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
		this.adminBearerTokenAuthenticationFilter = adminBearerTokenAuthenticationFilter;
		this.objectMapper = objectMapper;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/app/auth/register", "/api/app/auth/login",
								"/api/admin/auth/login",
								"/api/internal/payments/recharge/callback",
								"/api/system/health", "/actuator/health")
						.permitAll()
						.requestMatchers("/api/app/home/recommend",
								"/api/app/content/search",
								"/api/app/content/shelves/**",
								"/api/app/content/books/*",
								"/api/app/content/books/*/episodes",
								"/api/app/social/books/*/comments")
						.access((authentication, context) -> new AuthorizationDecision(
								context.getRequest().getAttribute(BearerTokenAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE)
										== null))
						.requestMatchers("/api/app/**").authenticated()
						.requestMatchers("/api/admin/**").authenticated()
						.anyRequest().permitAll())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) -> writeAuthError(request, response))
						.accessDeniedHandler((request, response, exception) ->
								writeError(request, response, HttpStatus.FORBIDDEN, "forbidden")))
				.addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		http.addFilterBefore(adminBearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	private void writeAuthError(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String failure = (String) request.getAttribute(BearerTokenAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
		if ("user disabled".equals(failure)) {
			writeError(request, response, HttpStatus.FORBIDDEN, "user disabled");
			return;
		}
		if ("token expired".equals(failure) || "token revoked".equals(failure)) {
			writeError(request, response, HttpStatus.UNAUTHORIZED, failure);
			return;
		}
		String adminFailure = (String) request.getAttribute(AdminBearerTokenAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
		if ("token expired".equals(adminFailure) || "token revoked".equals(adminFailure)) {
			writeError(request, response, HttpStatus.UNAUTHORIZED, adminFailure);
			return;
		}
		writeError(request, response, HttpStatus.UNAUTHORIZED, "unauthorized");
	}

	private void writeError(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
			throws IOException {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString();
			response.setHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);
		}
		response.setStatus(status.value());
		response.setContentType("application/json");
		objectMapper.writeValue(response.getWriter(),
				ApiErrorResponse.of(status.value(), message, request.getRequestURI(), requestId));
	}
}
