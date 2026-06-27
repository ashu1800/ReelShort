package com.reelshort.backend.payment;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentCallbackControllerTests {

	private static final String CALLBACK_SECRET = "dev-payment-callback-secret";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void callbackRequiresSecret() throws Exception {
		mockMvc.perform(post("/api/internal/payments/recharge/callback")
				.contentType(MediaType.APPLICATION_JSON)
				.content(callbackBody("evt-no-secret", "RO-missing", 990)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void callbackRejectsWrongSecret() throws Exception {
		mockMvc.perform(post("/api/internal/payments/recharge/callback")
				.header("X-Payment-Callback-Secret", "wrong")
				.contentType(MediaType.APPLICATION_JSON)
				.content(callbackBody("evt-wrong-secret", "RO-missing", 990)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("unauthorized"));
	}

	@Test
	void validCallbackSettlesOrder() throws Exception {
		RegisteredUser user = registerAppUser("payment-callback-alice");
		String orderNo = createOrder(user.token(), 990, 99);

		mockMvc.perform(post("/api/internal/payments/recharge/callback")
				.header("X-Payment-Callback-Secret", CALLBACK_SECRET)
				.contentType(MediaType.APPLICATION_JSON)
				.content(callbackBody("evt-controller-success", orderNo, 990)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PROCESSED"))
				.andExpect(jsonPath("$.data.orderStatus").value("PAID"));

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].source").value("RECHARGE_ORDER"));
	}

	@Test
	void duplicateCallbackDoesNotCreditTwice() throws Exception {
		RegisteredUser user = registerAppUser("payment-callback-bob");
		String orderNo = createOrder(user.token(), 990, 99);
		String body = callbackBody("evt-controller-duplicate", orderNo, 990);

		postCallback(body).andExpect(status().isOk());
		postCallback(body).andExpect(status().isOk());

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)));
	}

	@Test
	void amountMismatchReturnsBadRequest() throws Exception {
		RegisteredUser user = registerAppUser("payment-callback-carol");
		String orderNo = createOrder(user.token(), 990, 99);

		mockMvc.perform(post("/api/internal/payments/recharge/callback")
				.header("X-Payment-Callback-Secret", CALLBACK_SECRET)
				.contentType(MediaType.APPLICATION_JSON)
				.content(callbackBody("evt-controller-mismatch", orderNo, 1990)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("payment amount mismatch"));

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));
	}

	private org.springframework.test.web.servlet.ResultActions postCallback(String body) throws Exception {
		return mockMvc.perform(post("/api/internal/payments/recharge/callback")
				.header("X-Payment-Callback-Secret", CALLBACK_SECRET)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private String callbackBody(String providerEventId, String orderNo, int amountCents) {
		return """
				{
				  "providerEventId": "%s",
				  "orderNo": "%s",
				  "paymentChannel": "mock-pay",
				  "amountCents": %d
				}
				""".formatted(providerEventId, orderNo, amountCents);
	}

	private RegisteredUser registerAppUser(String username) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123"
						}
						""".formatted(username)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return new RegisteredUser(response.path("data").path("token").asText());
	}

	private String createOrder(String token, int amountCents, int pointAmount) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/app/orders/recharge")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amountCents": %d,
						  "pointAmount": %d
						}
						""".formatted(amountCents, pointAmount)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("orderNo").asText();
	}

	private record RegisteredUser(String token) {
	}
}
