package com.reelshort.backend.system.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.reelshort.backend.content.ContentProviderException;
import com.reelshort.backend.system.api.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoHandlerFound(NoHandlerFoundException exception,
			HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "resource not found", request);
	}

	@ExceptionHandler({
			MissingServletRequestParameterException.class,
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
		HttpStatus status = HttpStatus.resolve(exception.statusCode());
		if (status == null) {
			status = HttpStatus.SERVICE_UNAVAILABLE;
		}
		return error(status, exception.getMessage(), request);
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
