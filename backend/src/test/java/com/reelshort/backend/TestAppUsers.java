package com.reelshort.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.auth.CaptchaService;

public final class TestAppUsers {

	private static volatile CaptchaService captchaServiceHolder;

	private TestAppUsers() {
	}

	/**
	 * Binds the captcha service used to solve captchas during test registration. Call once from a test
	 * setup that has access to the Spring context.
	 *
	 * P2-16: 验证码答案只以 SHA-256 哈希落库，测试无法再从数据库读出明文答案，
	 * 因此改为直接调用 {@link CaptchaService#generate()}：生成结果里仍带有内存中的明文答案。
	 */
	public static void bindCaptchaService(CaptchaService captchaService) {
		captchaServiceHolder = captchaService;
	}

	private static CaptchaService captchaService() {
		CaptchaService captchaService = captchaServiceHolder;
		if (captchaService == null) {
			throw new IllegalStateException(
					"CaptchaService not bound; call TestAppUsers.bindCaptchaService(...) first");
		}
		return captchaService;
	}

	public static RegisteredUser register(MockMvc mockMvc, ObjectMapper objectMapper, String seed) throws Exception {
		return register(mockMvc, objectMapper, seed, "Password123");
	}

	public static RegisteredUser register(MockMvc mockMvc, ObjectMapper objectMapper, String seed, String password)
			throws Exception {
		String username = usernameFor(seed);
		CaptchaService.GeneratedCaptcha challenge = captchaService().generate();
		String captchaId = challenge.captchaId();
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
