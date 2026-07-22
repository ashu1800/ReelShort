package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.admin.AdminAuditLogRepository;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"reelshort.rate-limit.enabled=true",
		"reelshort.rate-limit.rules[0].name=admin-vip-mutation",
		"reelshort.rate-limit.rules[0].method=POST",
		"reelshort.rate-limit.rules[0].path=/api/admin/vip/orders/**",
		"reelshort.rate-limit.rules[0].limit=1",
		"reelshort.rate-limit.rules[0].window=60s"
})
class SensitiveAdminRateLimitTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private AdminAuditLogRepository auditLogs;

	@Test
	void rateLimitedVipFailureDoesNotCreateAnotherAuditRecord() throws Exception {
		String token = adminLogin();
		String ip = "203.0.113.32";
		UUID orderId = UUID.randomUUID();
		String body = "{\"txHash\":\"%s\"}".formatted("a".repeat(64));

		mockMvc.perform(post("/api/admin/vip/orders/{orderId}/confirm", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/admin/vip/orders/{orderId}/confirm", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header("X-Forwarded-For", ip)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isTooManyRequests());

		assertThat(auditLogs.findAll().stream()
				.filter(log -> orderId.equals(log.targetId()))
				.filter(log -> "VIP_ORDER_CONFIRM_FAILED".equals(log.action())))
				.hasSize(1);
	}

	private String adminLogin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"admin\",\"password\":\"Admin123\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("token").asText();
	}
}
