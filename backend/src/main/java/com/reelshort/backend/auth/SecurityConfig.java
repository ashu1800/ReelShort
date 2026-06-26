package com.reelshort.backend.auth;

import java.io.IOException;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.system.api.ApiErrorResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

	private final BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter;
	private final ObjectMapper objectMapper;

	public SecurityConfig(BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter, ObjectMapper objectMapper) {
		this.bearerTokenAuthenticationFilter = bearerTokenAuthenticationFilter;
		this.objectMapper = objectMapper;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/app/auth/register", "/api/app/auth/login",
								"/api/system/health", "/actuator/health")
						.permitAll()
						.requestMatchers("/api/app/**").authenticated()
						.requestMatchers("/api/admin/**").denyAll()
						.anyRequest().permitAll())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) -> writeAuthError(request, response))
						.accessDeniedHandler((request, response, exception) ->
								writeError(request, response, HttpStatus.FORBIDDEN, "forbidden")))
				.addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	private void writeAuthError(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String failure = (String) request.getAttribute(BearerTokenAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
		if ("user disabled".equals(failure)) {
			writeError(request, response, HttpStatus.FORBIDDEN, "user disabled");
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
