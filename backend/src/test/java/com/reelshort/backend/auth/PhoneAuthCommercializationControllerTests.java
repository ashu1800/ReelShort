package com.reelshort.backend.auth;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.reelshort.backend.user.UserAccountRepository;

@SpringBootTest(properties = "reelshort.internal.super-token=test-super-token")
@AutoConfigureMockMvc
class PhoneAuthCommercializationControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

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
						  "verificationCode": "000000"
						}
						"""))
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
						  "verificationCode": "000000"
						}
						"""))
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
}
