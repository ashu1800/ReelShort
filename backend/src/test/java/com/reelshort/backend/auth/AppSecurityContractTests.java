package com.reelshort.backend.auth;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.content.ContentBook;
import com.reelshort.backend.content.ContentLocale;
import com.reelshort.backend.content.ContentProvider;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;
import com.reelshort.backend.user.UserStatus;

@SpringBootTest
@AutoConfigureMockMvc
class AppSecurityContractTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private TokenHasher tokenHasher;

	@Autowired
	private AccessTokenRepository accessTokenRepository;

	@MockitoBean
	private ContentProvider contentProvider;

	@MockitoBean
	private SmsCallbackClient smsCallbackClient;

	@Test
	void authEndpointsRemainPublic() throws Exception {
		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "PUBLIC_REGISTER",
						  "countryCode": "+1",
						  "phoneNumber": "4155550401"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresInSeconds").value(120));

		mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "countryCode": "+1",
						  "phoneNumber": "4155550401",
						  "password": "Password123",
						  "verificationCode": "%s"
						}
						""".formatted(latestSmsCode())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SIMULATED"));
	}

	@Test
	void malformedJsonReturnsBadRequestInsteadOfInternalServerError() throws Exception {
		mockMvc.perform(post("/api/app/auth/sms/send")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\\\"purpose\\\":\\\"PUBLIC_REGISTER\\\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("bad request"));
	}

	@Test
	void contentSearchIsPublicForGuestBrowsing() throws Exception {
		when(contentProvider.search("love", ContentLocale.ENGLISH)).thenReturn(List.of(
				new ContentBook("book-guest", "Guest Love", "guest-love", "https://example.com/cover.jpg",
						"Guest browsable.", 12)));

		mockMvc.perform(get("/api/app/content/search").param("keywords", "love"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data[0].bookId").value("book-guest"))
				.andExpect(jsonPath("$.requestId").isNotEmpty())
				.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void contentSearchRejectsInvalidBearerTokenWhenProvided() throws Exception {
		mockMvc.perform(get("/api/app/content/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
				.param("keywords", "love"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void contentSearchRejectsDisabledUserToken() throws Exception {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("security-disabled", passwordHasher.hash("Password123"), UserStatus.DISABLED));
		String token = tokenService.issue(user).token();

		mockMvc.perform(get("/api/app/content/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.param("keywords", "love"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403));
	}

	@Test
	void contentSearchRejectsExpiredBearerToken() throws Exception {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("security-expired", passwordHasher.hash("Password123"), UserStatus.ACTIVE));
		String rawToken = "expired-token";
		accessTokenRepository.save(AccessToken.issue(
				tokenHasher.hash(rawToken),
				user,
				OffsetDateTime.now().minusDays(2),
				OffsetDateTime.now().minusDays(1)));

		mockMvc.perform(get("/api/app/content/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken)
				.param("keywords", "love"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.message").value("token expired"));
	}

	@Test
	void contentSearchRejectsRevokedBearerToken() throws Exception {
		UserAccount user = userAccountRepository.save(
				UserAccount.create("security-revoked", passwordHasher.hash("Password123"), UserStatus.ACTIVE));
		String rawToken = "revoked-token";
		AccessToken accessToken = AccessToken.issue(
				tokenHasher.hash(rawToken),
				user,
				OffsetDateTime.now().minusHours(1),
				OffsetDateTime.now().plusDays(1));
		accessToken.revoke(OffsetDateTime.now().minusMinutes(1));
		accessTokenRepository.save(accessToken);

		mockMvc.perform(get("/api/app/content/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken)
				.param("keywords", "love"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.message").value("token revoked"));
	}

	@Test
	void contentSearchAcceptsValidBearerToken() throws Exception {
		String token = TestAppUsers.token(mockMvc, objectMapper, "security-active");
		when(contentProvider.search("love", ContentLocale.ENGLISH)).thenReturn(List.of(
				new ContentBook("book-1", "Love Story", "love-story", "https://example.com/cover.jpg",
						"Authenticated browse.", 12)));

		mockMvc.perform(get("/api/app/content/search")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.param("keywords", "love"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data[0].bookId").value("book-1"));
	}

	@Test
	void contentPlayStillRequiresBearerToken() throws Exception {
		mockMvc.perform(get("/api/app/content/books/book-1/episodes/1/play")
				.param("filteredTitle", "love-story")
				.param("chapterId", "chapter-1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void adminApiIsDeniedUntilAdminSecurityExists() throws Exception {
		mockMvc.perform(get("/api/admin/users"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	private String registerAndExtractToken(String username) throws Exception {
		return TestAppUsers.token(mockMvc, objectMapper, username);
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
