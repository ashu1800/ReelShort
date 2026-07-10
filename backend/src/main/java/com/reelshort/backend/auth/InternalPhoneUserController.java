package com.reelshort.backend.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reelshort.backend.system.api.ApiResponse;
import com.reelshort.backend.system.web.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@RestController
@RequestMapping("/api/internal/users")
public class InternalPhoneUserController {

	private final AuthService authService;
	private final String superToken;
	private final ObjectMapper objectMapper;
	private final Validator validator;

	public InternalPhoneUserController(AuthService authService,
			@Value("${reelshort.internal.super-token:}") String superToken,
			ObjectMapper objectMapper,
			Validator validator) {
		this.authService = authService;
		this.superToken = superToken;
		this.objectMapper = objectMapper;
		this.validator = validator;
	}

	@PostMapping("/register-phone")
	public ApiResponse<?> registerPhone(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			@RequestBody JsonNode requestBody,
			HttpServletRequest httpRequest) {
		validateSuperToken(providedToken);
		if (requestBody.has("accounts")) {
			InternalPhoneBatchRegisterRequest request = readRequest(requestBody, InternalPhoneBatchRegisterRequest.class);
			return ApiResponse.success(registerBatch(request), requestId(httpRequest));
		}
		InternalPhoneRegisterRequest request = readRequest(requestBody, InternalPhoneRegisterRequest.class);
		return ApiResponse.success(authService.internalRegisterPhone(request.countryCode(), request.phoneNumber(),
				request.password()), requestId(httpRequest));
	}

	@PostMapping("/register-phone/batch")
	public ApiResponse<InternalPhoneBatchRegisterResponse> registerPhones(
			@RequestHeader(name = "X-Internal-Super-Token", required = false) String providedToken,
			@Valid @RequestBody InternalPhoneBatchRegisterRequest request,
			HttpServletRequest httpRequest) {
		validateSuperToken(providedToken);
		return ApiResponse.success(registerBatch(request), requestId(httpRequest));
	}

	private InternalPhoneBatchRegisterResponse registerBatch(InternalPhoneBatchRegisterRequest request) {
		List<InternalPhoneBatchRegisterResponse.Result> results = new ArrayList<>();
		for (int index = 0; index < request.accounts().size(); index++) {
			InternalPhoneRegisterRequest account = request.accounts().get(index);
			try {
				AuthToken token = authService.internalRegisterPhone(account.countryCode(), account.phoneNumber(),
						account.password());
				results.add(InternalPhoneBatchRegisterResponse.Result.success(index, account, token));
			}
			catch (AuthException exception) {
				results.add(InternalPhoneBatchRegisterResponse.Result.failure(index, account,
						batchErrorCode(exception.getMessage()), exception.getMessage()));
			}
		}
		return InternalPhoneBatchRegisterResponse.from(results);
	}

	private String batchErrorCode(String message) {
		if ("phone already exists".equals(message)) {
			return "PHONE_ALREADY_EXISTS";
		}
		if ("unsupported phone country code".equals(message)) {
			return "UNSUPPORTED_PHONE_COUNTRY_CODE";
		}
		if ("invalid phone number".equals(message)) {
			return "INVALID_PHONE_NUMBER";
		}
		return "REGISTRATION_FAILED";
	}

	private <T> T readRequest(JsonNode requestBody, Class<T> type) {
		try {
			T request = objectMapper.treeToValue(requestBody, type);
			Set<ConstraintViolation<T>> violations = validator.validate(request);
			if (!violations.isEmpty()) {
				throw new AuthException(400, "bad request");
			}
			return request;
		}
		catch (JsonProcessingException exception) {
			throw new AuthException(400, "bad request");
		}
	}

	private void validateSuperToken(String providedToken) {
		if (providedToken == null || providedToken.isBlank()) {
			throw new AuthException(401, "unauthorized");
		}
		if (superToken.isBlank() || !superToken.equals(providedToken)) {
			throw new AuthException(403, "forbidden");
		}
	}

	private String requestId(HttpServletRequest request) {
		return (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
	}
}
