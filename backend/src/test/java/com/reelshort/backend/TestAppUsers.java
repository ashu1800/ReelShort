package com.reelshort.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class TestAppUsers {

	private TestAppUsers() {
	}

	public static RegisteredUser register(MockMvc mockMvc, ObjectMapper objectMapper, String seed) throws Exception {
		return register(mockMvc, objectMapper, seed, "Password123");
	}

	public static RegisteredUser register(MockMvc mockMvc, ObjectMapper objectMapper, String seed, String password)
			throws Exception {
		String phoneNumber = phoneNumberFor(seed);
		String body = """
				{
				  "countryCode": "+1",
				  "phoneNumber": "%s",
				  "password": "%s"
				}
				""".formatted(phoneNumber, password);
		String responseBody = mockMvc.perform(post("/api/internal/users/register-phone")
				.header("X-Internal-Super-Token", "test-super-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode response = objectMapper.readTree(responseBody);
		return new RegisteredUser(UUID.fromString(response.path("data").path("userId").asText()),
				response.path("data").path("token").asText(), response.path("data").path("username").asText(),
				"+1", phoneNumber);
	}

	public static String token(MockMvc mockMvc, ObjectMapper objectMapper, String seed) throws Exception {
		return register(mockMvc, objectMapper, seed).token();
	}

	public static String phoneNumberFor(String seed) {
		return "415%07d".formatted(Math.floorMod(seed.hashCode(), 10_000_000));
	}

	public record RegisteredUser(UUID userId, String token, String username, String countryCode, String phoneNumber) {
	}
}
