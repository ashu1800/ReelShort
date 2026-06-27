package com.reelshort.backend.system.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.content.ContentProviderException;
import com.reelshort.backend.payment.PaymentException;
import com.reelshort.backend.system.api.ApiErrorResponse;
import com.reelshort.backend.system.logs.SystemLogException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler({
			NoHandlerFoundException.class,
			NoResourceFoundException.class
	})
	public ResponseEntity<ApiErrorResponse> handleNoHandlerFound(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "resource not found", request);
	}

	@ExceptionHandler({
			MissingServletRequestParameterException.class,
			MethodArgumentNotValidException.class,
			MethodArgumentTypeMismatchException.class,
			ConstraintViolationException.class,
			HandlerMethodValidationException.class,
			MethodValidationException.class
	})
	public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "bad request", request);
	}

	@ExceptionHandler(ContentProviderException.class)
	public ResponseEntity<ApiErrorResponse> handleContentProviderException(ContentProviderException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException exception, HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(AdminException.class)
	public ResponseEntity<ApiErrorResponse> handleAdminException(AdminException exception, HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(PaymentException.class)
	public ResponseEntity<ApiErrorResponse> handlePaymentException(PaymentException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(SystemLogException.class)
	public ResponseEntity<ApiErrorResponse> handleSystemLogException(SystemLogException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	private HttpStatus resolveStatus(int statusCode) {
		HttpStatus status = HttpStatus.resolve(statusCode);
		return status == null ? HttpStatus.SERVICE_UNAVAILABLE : status;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleException(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", request);
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ResponseEntity.status(status)
				.body(ApiErrorResponse.of(status.value(), message, request.getRequestURI(), requestId));
	}
}
