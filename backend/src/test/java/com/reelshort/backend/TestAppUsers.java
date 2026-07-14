package com.reelshort.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.reelshort.backend.auth.CaptchaChallenge;
import com.reelshort.backend.auth.CaptchaChallengeRepository;

public final class TestAppUsers {

	private static volatile CaptchaChallengeRepository captchaRepositoryHolder;

	private TestAppUsers() {
	}

	/**
	 * Binds the captcha repository used to solve captmas during test registration. Call once from a test
	 * setup that has access to the Spring context.
	 */
	public static void bindCaptchaRepository(CaptchaChallengeRepository repository) {
		captchaRepositoryHolder = repository;
	}

	private static CaptchaChallengeRepository captchaRepository() {
		CaptchaChallengeRepository repository = captchaRepositoryHolder;
		if (repository == null) {
			throw new IllegalStateException(
					"CaptchaChallengeRepository not bound; call TestAppUsers.bindCaptchaRepository(...) first");
		}
		return repository;
	}

	public static RegisteredUser register(MockMvc mockMvc, ObjectMapper objectMapper, String seed) throws Exception {
		return register(mockMvc, objectMapper, seed, "Password123");
	}

	public static RegisteredUser register(MockMvc mockMvc, ObjectMapper objectMapper, String seed, String password)
			throws Exception {
		String username = usernameFor(seed);
		MvcResult captchaResult = mockMvc.perform(get("/api/app/auth/captcha"))
				.andExpect(status().isOk())
				.andReturn();
		String captchaBody = captchaResult.getResponse().getContentAsString();
		String captchaId = JsonPath.read(captchaBody, "$.data.captchaId");
		CaptchaChallenge challenge = captchaRepository().findById(UUID.fromString(captchaId)).orElseThrow();
		String captchaAnswer = challenge.answer();
		String body = """
				{
				  "username": "%s",
				  "password": "%s",
				  "captchaId": "%s",
				  "captchaAnswer": "%s"
				}
				""".formatted(username, password, captchaId, captchaAnswer);
		String responseBody = mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode response = objectMapper.readTree(responseBody);
		return new RegisteredUser(UUID.fromString(response.path("data").path("userId").asText()),
				response.path("data").path("token").asText(), response.path("data").path("username").asText());
	}

	public static String token(MockMvc mockMvc, ObjectMapper objectMapper, String seed) throws Exception {
		return register(mockMvc, objectMapper, seed).token();
	}

	public static String usernameFor(String seed) {
		return "user" + Math.floorMod(seed.hashCode() & 0x7fffffff, 1_000_000_000);
	}

	public record RegisteredUser(UUID userId, String token, String username) {
	}
}
