package com.reelshort.backend.auth;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
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

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	@MockitoBean
	private ContentProvider contentProvider;

	@Test
	void loginReturnsTokenForValidPhoneCredentials() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "auth-controller-login");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "countryCode": "%s",
						  "phoneNumber": "%s",
						  "password": "Password123"
						}
						""".formatted(user.countryCode(), user.phoneNumber())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.username").value(user.username()))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"));
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
						  "countryCode": "%s",
						  "phoneNumber": "%s",
						  "password": "wrong"
						}
						""".formatted(user.countryCode(), user.phoneNumber())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.message").value("invalid phone or password"));
	}

	@Test
	void registerRejectsOldUsernameShape() throws Exception {
		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "legacy",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.path").value("/api/app/auth/register"));
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
