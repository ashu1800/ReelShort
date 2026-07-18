package com.reelshort.backend.order;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
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

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.TestAppUsers;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RechargeOrderRepository rechargeOrderRepository;

	@Autowired
	private RechargeOrderService rechargeOrderService;

	@Test
	void createRechargeOrderRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/app/orders/recharge")
				.contentType(MediaType.APPLICATION_JSON)
				.content(rechargeBody(990, 99)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void authenticatedUserCannotCreateUnsupportedRechargeOrder() throws Exception {
		String token = registerAndExtractToken("order-alice");
		long before = rechargeOrderRepository.count();

		mockMvc.perform(post("/api/app/orders/recharge")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(rechargeBody(990, 99)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.message").value("recharge is not supported"));
		org.assertj.core.api.Assertions.assertThat(rechargeOrderRepository.count()).isEqualTo(before);
	}

	@Test
	void orderListIsScopedToCurrentUserAndNewestFirst() throws Exception {
		TestAppUsers.RegisteredUser bob = TestAppUsers.register(mockMvc, objectMapper, "order-bob");
		TestAppUsers.RegisteredUser carol = TestAppUsers.register(mockMvc, objectMapper, "order-carol");
		String token = bob.token();
		UUID bobId = bob.userId();
		UUID carolId = carol.userId();
		rechargeOrderService.create(bobId, new CreateRechargeOrderRequest(990, 99));
		rechargeOrderService.create(carolId, new CreateRechargeOrderRequest(2990, 299));
		rechargeOrderService.create(bobId, new CreateRechargeOrderRequest(1990, 199));

		mockMvc.perform(get("/api/app/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].amountCents").value(1990))
				.andExpect(jsonPath("$.data[1].amountCents").value(990));
	}

	@Test
	void unsupportedRechargeOrderReturnsBusinessErrorEvenForLegacyPayload() throws Exception {
		String token = registerAndExtractToken("order-dan");

		mockMvc.perform(post("/api/app/orders/recharge")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(rechargeBody(0, 99)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.message").value("recharge is not supported"));
	}

	private String rechargeBody(int amountCents, int pointAmount) {
		return """
				{
				  "amountCents": %d,
				  "pointAmount": %d
				}
				""".formatted(amountCents, pointAmount);
	}

	private String registerAndExtractToken(String username) throws Exception {
		return TestAppUsers.token(mockMvc, objectMapper, username);
	}
}
