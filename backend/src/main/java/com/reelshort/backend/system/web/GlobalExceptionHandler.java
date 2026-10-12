package com.reelshort.backend.system.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.reelshort.backend.admin.AdminException;
import com.reelshort.backend.auth.AuthException;
import com.reelshort.backend.content.ContentProviderException;
import com.reelshort.backend.payment.PaymentException;
import com.reelshort.backend.order.OrderException;
import com.reelshort.backend.release.ReleaseException;
import com.reelshort.backend.social.SocialException;
import com.reelshort.backend.system.api.ApiErrorResponse;
import com.reelshort.backend.withdrawal.WithdrawalException;
import com.reelshort.backend.system.alerts.SystemAlertException;
import com.reelshort.backend.system.logs.SystemLogException;
import com.reelshort.backend.system.security.CredentialLockedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler({
			NoHandlerFoundException.class,
			NoResourceFoundException.class
	})
	public ResponseEntity<ApiErrorResponse> handleNoHandlerFound(Exception exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "resource not found", request);
	}

	@ExceptionHandler({
			MissingServletRequestParameterException.class,
			HttpMessageNotReadableException.class,
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

	@ExceptionHandler(OrderException.class)
	public ResponseEntity<ApiErrorResponse> handleOrderException(OrderException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(SystemLogException.class)
	public ResponseEntity<ApiErrorResponse> handleSystemLogException(SystemLogException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(SystemAlertException.class)
	public ResponseEntity<ApiErrorResponse> handleSystemAlertException(SystemAlertException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(SocialException.class)
	public ResponseEntity<ApiErrorResponse> handleSocialException(SocialException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(ReleaseException.class)
	public ResponseEntity<ApiErrorResponse> handleReleaseException(ReleaseException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	@ExceptionHandler(WithdrawalException.class)
	public ResponseEntity<ApiErrorResponse> handleWithdrawalException(WithdrawalException exception,
			HttpServletRequest request) {
		return error(resolveStatus(exception.statusCode()), exception.getMessage(), request);
	}

	/**
	 * P1-5: 凭证（改密 / 钱包改绑密码）在锁定窗口内被拒绝 → 429。
	 * message 固定为 "too many attempts, try again later"，不泄露剩余次数。
	 */
	@ExceptionHandler(CredentialLockedException.class)
	public ResponseEntity<ApiErrorResponse> handleCredentialLockedException(CredentialLockedException exception,
			HttpServletRequest request) {
		return error(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request);
	}

	/**
	 * P2-17: VIP 订单、提现等状态机的非法状态转换抛 IllegalStateException，属于业务冲突（409）而不是服务端故障。
	 * 响应文案固定为 conflict，不回显异常消息（异常消息可能包含私钥等敏感输入，见 P2-8）。
	 */
	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalStateException(IllegalStateException exception,
			HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		log.warn("Request rejected with conflict. requestId={} path={} message={}", requestId,
				request.getRequestURI(), exception.getMessage());
		return error(HttpStatus.CONFLICT, "conflict", request);
	}

	/**
	 * P2-17: 超长字段等落库约束失败返回 400，避免客户端可触发的数据问题变成 500。
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolationException(
			DataIntegrityViolationException exception, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		log.warn("Request rejected by data integrity constraint. requestId={} path={}", requestId,
				request.getRequestURI());
		return error(HttpStatus.BAD_REQUEST, "bad request", request);
	}

	private HttpStatus resolveStatus(int statusCode) {
		HttpStatus status = HttpStatus.resolve(statusCode);
		return status == null ? HttpStatus.SERVICE_UNAVAILABLE : status;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleException(Exception exception, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		log.error("Unhandled request exception. requestId={} path={}", requestId, request.getRequestURI(), exception);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", request);
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, HttpServletRequest request) {
		String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
		return ResponseEntity.status(status)
				.body(ApiErrorResponse.of(status.value(), message, request.getRequestURI(), requestId));
	}
}
