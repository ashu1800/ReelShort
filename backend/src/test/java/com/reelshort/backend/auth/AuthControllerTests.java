package com.reelshort.backend.auth;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.content.ContentBook;
import com.reelshort.backend.content.ContentLocale;
import com.reelshort.backend.content.ContentProvider;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@MockitoBean
	private ContentProvider contentProvider;

	@Test
	void loginReturnsTokenForAppCredentials() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "auth-controller-login");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123",
						  "loginSource": "APP"
						}
						""".formatted(user.username())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.username").value(user.username()))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"));
	}

	@Test
	void loginRejectsMissingLoginSource() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "auth-controller-missing-source");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123"
						}
						""".formatted(user.username())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.path").value("/api/app/auth/login"));
	}

	@Test
	void loginRejectsUnknownLoginSource() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "auth-controller-unknown-source");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123",
						  "loginSource": "BATCH"
						}
						""".formatted(user.username())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.path").value("/api/app/auth/login"));
	}

	@Test
	void scriptLoginIsRejectedBeforeAppLogin() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "auth-controller-script-block");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123",
						  "loginSource": "SCRIPT"
						}
						""".formatted(user.username())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403))
				.andExpect(jsonPath("$.message").value("请先在 App 登录一次后再使用脚本登录"));
	}

	@Test
	void appLoginMarksFirstAppLoginTimeOnce() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "auth-controller-first-app-login");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123",
						  "loginSource": "APP"
						}
						""".formatted(user.username())))
				.andExpect(status().isOk());

		UserAccount account = userAccountRepository.findByUsername(user.username()).orElseThrow();
		assertThat(account.firstAppLoginAt()).isNotNull();
	}

	@Test
	void secondAppLoginDoesNotOverwriteFirstAppLoginTime() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "auth-controller-first-app-login-repeat");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123",
						  "loginSource": "APP"
						}
						""".formatted(user.username())))
				.andExpect(status().isOk());

		UserAccount first = userAccountRepository.findByUsername(user.username()).orElseThrow();
		Object firstLoginAt = first.firstAppLoginAt();

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123",
						  "loginSource": "APP"
						}
						""".formatted(user.username())))
				.andExpect(status().isOk());

		UserAccount second = userAccountRepository.findByUsername(user.username()).orElseThrow();
		assertThat(second.firstAppLoginAt()).isEqualTo(firstLoginAt);
	}

	@Test
	void loginTokenCanAccessProtectedAppApi() throws Exception {
		String token = TestAppUsers.token(mockMvc, objectMapper, "auth-controller-api");
		when(contentProvider.search("love", ContentLocale.ENGLISH)).thenReturn(List.of(
				new ContentBook("book-login-token", "Love", "love", "https://example.com/cover.jpg", "", 3)));

		mockMvc.perform(get("/api/app/content/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.param("keywords", "love"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].bookId").value("book-login-token"));
	}

	@Test
	void loginRejectsInvalidPassword() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "auth-controller-invalid");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "wrong",
						  "loginSource": "APP"
						}
						""".formatted(user.username())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.message").value("invalid username or password"));
	}

	@Test
	void registerRejectsOldUsernameShape() throws Exception {
		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "legacy",
						  "password": "Password123",
						  "captchaId": "00000000-0000-0000-0000-000000000000",
						  "captchaAnswer": "AAAA"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.path").value("/api/app/auth/register"));
	}

	@Test
	void registerAndPasswordChangeRejectSevenCharacterPasswords() throws Exception {
		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "password-seven",
						  "password": "1234567",
						  "captchaId": "00000000-0000-0000-0000-000000000000",
						  "captchaAnswer": "AAAA"
						}
						"""))
				.andExpect(status().isBadRequest());

		String token = TestAppUsers.token(mockMvc, objectMapper, "password-change-seven");
		mockMvc.perform(post("/api/app/auth/password/change")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "oldPassword": "Password123",
						  "newPassword": "1234567"
						}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void logoutRevokesCurrentBearerToken() throws Exception {
		String token = TestAppUsers.token(mockMvc, objectMapper, "auth-controller-logout");

		mockMvc.perform(post("/api/app/auth/logout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data").value("logged out"));

		mockMvc.perform(get("/api/app/content/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.param("keywords", "love"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("token revoked"));
	}

}
