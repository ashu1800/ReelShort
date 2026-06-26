package com.reelshort.backend.system;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"reelshort.rate-limit.enabled=true",
		"reelshort.rate-limit.rules[0].name=app-login",
		"reelshort.rate-limit.rules[0].method=POST",
		"reelshort.rate-limit.rules[0].path=/api/app/auth/login",
		"reelshort.rate-limit.rules[0].limit=1",
		"reelshort.rate-limit.rules[0].window=60s"
})
class RateLimitInterceptorTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsTooManyRequestsWhenRuleLimitIsExceeded() throws Exception {
		loginFromIp("203.0.113.10").andExpect(status().isUnauthorized());

		loginFromIp("203.0.113.10")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", notNullValue()))
				.andExpect(jsonPath("$.code").value(429))
				.andExpect(jsonPath("$.message").value("too many requests"))
				.andExpect(jsonPath("$.path").value("/api/app/auth/login"));
	}

	@Test
	void usesSeparateCountersForDifferentIps() throws Exception {
		loginFromIp("203.0.113.20").andExpect(status().isUnauthorized());

		loginFromIp("203.0.113.21").andExpect(status().isUnauthorized());
	}

	@Test
	void unconfiguredHealthEndpointIsNotRateLimited() throws Exception {
		mockMvc.perform(get("/api/system/health"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/system/health"))
				.andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions loginFromIp(String ip) throws Exception {
		return mockMvc.perform(post("/api/app/auth/login")
				.header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "missing-user",
						  "password": "Password123"
						}
						"""));
	}
}
