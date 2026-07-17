package com.reelshort.backend.admin;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.system.api.ApiErrorResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

	private final ObjectMapper objectMapper;

	public AdminPermissionInterceptor(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}
		RequireAdminPermission permission = permission(handlerMethod);
		RequireAdminPermissions permissions = permissions(handlerMethod);
		if (permission == null && permissions == null) {
			return true;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal principal)) {
			writeForbidden(request, response);
			return false;
		}
		boolean allowed = permissions != null
				? principal.permissions().containsAll(java.util.List.of(permissions.value()))
				: principal.permissions().contains(permission.value());
		if (!allowed) {
			writeForbidden(request, response);
			return false;
		}
		return true;
	}

	private RequireAdminPermission permission(HandlerMethod handlerMethod) {
		RequireAdminPermission methodPermission = AnnotatedElementUtils.findMergedAnnotation(
				handlerMethod.getMethod(), RequireAdminPermission.class);
		if (methodPermission != null) {
			return methodPermission;
		}
		return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireAdminPermission.class);
	}

	private RequireAdminPermissions permissions(HandlerMethod handlerMethod) {
		RequireAdminPermissions methodPermissions = AnnotatedElementUtils.findMergedAnnotation(
				handlerMethod.getMethod(), RequireAdminPermissions.class);
		if (methodPermissions != null) {
			return methodPermissions;
		}
		return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireAdminPermissions.class);
	}

	private void writeForbidden(HttpServletRequest request, HttpServletResponse response) throws Exception {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType("application/json");
		objectMapper.writeValue(response.getWriter(),
				ApiErrorResponse.of(HttpStatus.FORBIDDEN.value(), "forbidden", request.getRequestURI(), requestId));
	}
}
