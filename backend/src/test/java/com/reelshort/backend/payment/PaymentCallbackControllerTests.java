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
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.order.CreateRechargeOrderRequest;
import com.reelshort.backend.order.RechargeOrderService;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentCallbackControllerTests {

	private static final String CALLBACK_SECRET = "dev-payment-callback-secret";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RechargeOrderService rechargeOrderService;

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
		String orderNo = createOrder(user, 990, 99);

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
		String orderNo = createOrder(user, 990, 99);
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
		String orderNo = createOrder(user, 990, 99);

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
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, username);
		return new RegisteredUser(user.userId(), user.token());
	}

	private String createOrder(RegisteredUser user, int amountCents, int pointAmount) {
		return rechargeOrderService.create(user.userId(), new CreateRechargeOrderRequest(amountCents, pointAmount)).orderNo();
	}

	private record RegisteredUser(java.util.UUID userId, String token) {
	}
}
