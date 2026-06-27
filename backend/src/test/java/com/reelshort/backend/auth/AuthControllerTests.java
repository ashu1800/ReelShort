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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.content.ContentBook;
import com.reelshort.backend.content.ContentProvider;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ContentProvider contentProvider;

	@Test
	void registerCreatesActiveUserAndReturnsToken() throws Exception {
		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "alice",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.userId", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.username").value("alice"))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"));
	}

	@Test
	void duplicateRegistrationReturnsConflict() throws Exception {
		registerUser("bob", "Password123");

		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "bob",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(409))
				.andExpect(jsonPath("$.message").value("username already exists"));
	}

	@Test
	void loginReturnsTokenForValidCredentials() throws Exception {
		registerUser("carol", "Password123");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "carol",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.username").value("carol"))
				.andExpect(jsonPath("$.data.token", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"));
	}

	@Test
	void loginTokenCanAccessProtectedAppApi() throws Exception {
		registerUser("carol-api", "Password123");
		String token = loginUserAndExtractToken("carol-api", "Password123");
		when(contentProvider.search("love")).thenReturn(List.of(
				new ContentBook("book-login-token", "Love", "love", "https://example.com/cover.jpg", 3)));

		mockMvc.perform(get("/api/app/content/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.param("keywords", "love"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].bookId").value("book-login-token"));
	}

	@Test
	void loginRejectsInvalidPassword() throws Exception {
		registerUser("dave", "Password123");

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "dave",
						  "password": "wrong"
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.message").value("invalid username or password"));
	}

	@Test
	void loginRejectsDisabledUser() throws Exception {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("erin", "already-hashed", UserStatus.DISABLED));

		mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "erin",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403))
				.andExpect(jsonPath("$.message").value("user disabled"))
				.andExpect(jsonPath("$.path").value("/api/app/auth/login"));
	}

	@Test
	void registerRejectsBlankUsername() throws Exception {
		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": " ",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.path").value("/api/app/auth/register"));
	}

	@Test
	void logoutRevokesCurrentBearerToken() throws Exception {
		String token = registerUserAndExtractToken("logout-user", "Password123");

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

	private void registerUser(String username, String password) throws Exception {
		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "%s"
						}
						""".formatted(username, password)))
				.andExpect(status().isOk());
	}

	private String registerUserAndExtractToken(String username, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "%s"
						}
						""".formatted(username, password)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}

	private String loginUserAndExtractToken(String username, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "%s"
						}
						""".formatted(username, password)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}
}
