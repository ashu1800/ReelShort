package com.reelshort.backend.auth;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reelshort.backend.user.UserAccountRepository;

@SpringBootTest(properties = "reelshort.internal.super-token=test-super-token")
@AutoConfigureMockMvc
class PhoneAuthCommercializationControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@MockitoBean
	private SmsCallbackClient smsCallbackClient;

	@Test
	void internalPhoneRegistrationRequiresSuperToken() throws Exception {
		mockMvc.perform(post("/api/internal/users/register-phone")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson("+1", "4155550199", "Password123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("unauthorized"));

		mockMvc.perform(post("/api/internal/users/register-phone")
				.header("X-Internal-Super-Token", "wrong-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson("+1", "4155550199", "Password123")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("forbidden"));
	}

	@Test
	void internalPhoneRegistrationCreatesLoginAccount() throws Exception {
		mockMvc.perform(post("/api/internal/users/register-phone")
				.header("X-Internal-Super-Token", "test-super-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson("+1", "4155550101", "Password123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value("+14155550101"))
				.andExpect(jsonPath("$.data.phoneE164").value("+14155550101"));

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson("+1", "4155550101", "Password123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value("+14155550101"))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"));
	}

	@Test
	void internalBatchPhoneRegistrationCreatesMultipleLoginAccounts() throws Exception {
		mockMvc.perform(post("/api/internal/users/register-phone")
				.header("X-Internal-Super-Token", "test-super-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "accounts": [
						    { "countryCode": "+1", "phoneNumber": "4155550201", "password": "Password123" },
						    { "countryCode": "+44", "phoneNumber": "7400123456", "password": "Password123" }
						  ]
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.total").value(2))
				.andExpect(jsonPath("$.data.successCount").value(2))
				.andExpect(jsonPath("$.data.failureCount").value(0))
				.andExpect(jsonPath("$.data.results[0].success").value(true))
				.andExpect(jsonPath("$.data.results[0].phoneE164").value("+14155550201"))
				.andExpect(jsonPath("$.data.results[0].token").isNotEmpty())
				.andExpect(jsonPath("$.data.results[1].success").value(true))
				.andExpect(jsonPath("$.data.results[1].phoneE164").value("+447400123456"))
				.andExpect(jsonPath("$.data.results[1].token").isNotEmpty());

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson("+44", "7400123456", "Password123")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.phoneE164").value("+447400123456"));
	}

	@Test
	void internalBatchPhoneRegistrationAlsoSupportsBatchAliasPath() throws Exception {
		mockMvc.perform(post("/api/internal/users/register-phone/batch")
				.header("X-Internal-Super-Token", "test-super-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "accounts": [
						    { "countryCode": "+1", "phoneNumber": "4155550205", "password": "Password123" }
						  ]
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.successCount").value(1))
				.andExpect(jsonPath("$.data.results[0].phoneE164").value("+14155550205"));
	}

	@Test
	void internalBatchPhoneRegistrationReturnsPerAccountFailures() throws Exception {
		registerPhoneUser("+1", "4155550202", "Password123");

		mockMvc.perform(post("/api/internal/users/register-phone/batch")
				.header("X-Internal-Super-Token", "test-super-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "accounts": [
						    { "countryCode": "+1", "phoneNumber": "4155550202", "password": "Password123" },
						    { "countryCode": "+1", "phoneNumber": "4155550203", "password": "Password123" }
						  ]
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.total").value(2))
				.andExpect(jsonPath("$.data.successCount").value(1))
				.andExpect(jsonPath("$.data.failureCount").value(1))
				.andExpect(jsonPath("$.data.results[0].success").value(false))
				.andExpect(jsonPath("$.data.results[0].phoneNumber").value("4155550202"))
				.andExpect(jsonPath("$.data.results[0].errorCode").value("PHONE_ALREADY_EXISTS"))
				.andExpect(jsonPath("$.data.results[0].message").value("phone already exists"))
				.andExpect(jsonPath("$.data.results[1].success").value(true))
				.andExpect(jsonPath("$.data.results[1].phoneE164").value("+14155550203"));
	}

	@Test
	void internalBatchPhoneRegistrationRequiresSuperToken() throws Exception {
		mockMvc.perform(post("/api/internal/users/register-phone/batch")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "accounts": [
						    { "countryCode": "+1", "phoneNumber": "4155550204", "password": "Password123" }
						  ]
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("unauthorized"));

		mockMvc.perform(post("/api/internal/users/register-phone/batch")
				.header("X-Internal-Super-Token", "wrong-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "accounts": [
						    { "countryCode": "+1", "phoneNumber": "4155550204", "password": "Password123" }
						  ]
						}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("forbidden"));
	}

	@Test
	void internalBatchPhoneRegistrationRejectsNullAccountItems() throws Exception {
		mockMvc.perform(post("/api/internal/users/register-phone")
				.header("X-Internal-Super-Token", "test-super-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "accounts": [null]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("bad request"));
	}

	@Test
	void publicRegisterSimulatesSmsButDoesNotCreateLoginAccount() throws Exception {
		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "PUBLIC_REGISTER",
						  "countryCode": "+1",
						  "phoneNumber": "4155550102"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresInSeconds").value(120));

		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "countryCode": "+1",
						  "phoneNumber": "4155550102",
						  "password": "Password123",
						  "verificationCode": "%s"
						}
						""".formatted(latestSmsCode())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SIMULATED"));

		assertThat(userAccountRepository.findByPhoneE164("+14155550102")).isEmpty();

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson("+1", "4155550102", "Password123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("invalid phone or password"));
	}

	@Test
	void publicRegisterWithWrongVerificationCodeReturnsSpecificMessage() throws Exception {
		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "PUBLIC_REGISTER",
						  "countryCode": "+1",
						  "phoneNumber": "4155550112"
						}
						"""))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "countryCode": "+1",
						  "phoneNumber": "4155550112",
						  "password": "Password123",
						  "verificationCode": "123456"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid verification code"));
	}

	@Test
	void publicSmsCallbackFailureReturnsBusinessError() throws Exception {
		doThrow(new SmsCallbackException("account manager rejected sms"))
				.when(smsCallbackClient)
				.send(any(SmsCallbackMessage.class));

		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "PUBLIC_REGISTER",
						  "countryCode": "+1",
						  "phoneNumber": "4155550113"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("verification code delivery failed"));
	}

	@Test
	void publicSmsAccountNotFoundReturnsSuccessButRegisterCodeIsInvalid() throws Exception {
		doThrow(new SmsAccountNotFoundException("account not found"))
				.when(smsCallbackClient)
				.send(any(SmsCallbackMessage.class));

		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "PUBLIC_REGISTER",
						  "countryCode": "+1",
						  "phoneNumber": "4155550114"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresInSeconds").value(120));

		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "countryCode": "+1",
						  "phoneNumber": "4155550114",
						  "password": "Password123",
						  "verificationCode": "123456"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid verification code"));
	}

	@Test
	void phoneAuthRejectsMainlandChinaNumbersAndOldUsernameLoginShape() throws Exception {
		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "PUBLIC_REGISTER",
						  "countryCode": "+86",
						  "phoneNumber": "13800138000"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("unsupported phone country code"));

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "legacy-user",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void publicSmsOnlyAllowsRegisterPurpose() throws Exception {
		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "PASSWORD_CHANGE",
						  "countryCode": "+1",
						  "phoneNumber": "4155550103"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("sms purpose not allowed"));

		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "WALLET_BIND",
						  "countryCode": "+1",
						  "phoneNumber": "4155550103"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("sms purpose not allowed"));
	}

	@Test
	void passwordChangeUsesProtectedSmsVerificationAndRevokesOldToken() throws Exception {
		registerPhoneUser("+1", "4155550103", "Password123");
		String token = loginToken("+1", "4155550103", "Password123");

		mockMvc.perform(post("/api/app/auth/password/verification/send")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresInSeconds").value(120));

		mockMvc.perform(post("/api/app/auth/password/change")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "oldPassword": "Password123",
						  "newPassword": "NewPassword123",
						  "verificationCode": "%s"
						}
						""".formatted(latestSmsCode())))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/app/wallet/verification/send")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "WALLET_BIND"
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("token revoked"));

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson("+1", "4155550103", "NewPassword123")))
				.andExpect(status().isOk());
	}

	@Test
	void passwordSmsAccountNotFoundReturnsSuccessButChangeCodeIsInvalid() throws Exception {
		registerPhoneUser("+1", "4155550104", "Password123");
		String token = loginToken("+1", "4155550104", "Password123");
		doThrow(new SmsAccountNotFoundException("account not found"))
				.when(smsCallbackClient)
				.send(any(SmsCallbackMessage.class));

		mockMvc.perform(post("/api/app/auth/password/verification/send")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresInSeconds").value(120));

		mockMvc.perform(post("/api/app/auth/password/change")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "oldPassword": "Password123",
						  "newPassword": "NewPassword123",
						  "verificationCode": "123456"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid verification code"));
	}

	private void registerPhoneUser(String countryCode, String phoneNumber, String password) throws Exception {
		mockMvc.perform(post("/api/internal/users/register-phone")
				.header("X-Internal-Super-Token", "test-super-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson(countryCode, phoneNumber, password)))
				.andExpect(status().isOk());
	}

	private String loginToken(String countryCode, String phoneNumber, String password) throws Exception {
		String response = mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(phoneRegistrationJson(countryCode, phoneNumber, password)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(response, "$.data.token");
	}

	private String phoneRegistrationJson(String countryCode, String phoneNumber, String password) {
		return """
				{
				  "countryCode": "%s",
				  "phoneNumber": "%s",
				  "password": "%s"
				}
				""".formatted(countryCode, phoneNumber, password);
	}

	private String latestSmsCode() {
		ArgumentCaptor<SmsCallbackMessage> captor = ArgumentCaptor.forClass(SmsCallbackMessage.class);
		verify(smsCallbackClient, atLeastOnce()).send(captor.capture());
		String content = captor.getAllValues().get(captor.getAllValues().size() - 1).content();
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("code is (\\d{6})\\.").matcher(content);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
