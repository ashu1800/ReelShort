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
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.TestAppUsers;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void createRechargeOrderRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/app/orders/recharge")
				.contentType(MediaType.APPLICATION_JSON)
				.content(rechargeBody(990, 99)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void authenticatedUserCreatesRechargeOrder() throws Exception {
		String token = registerAndExtractToken("order-alice");

		mockMvc.perform(post("/api/app/orders/recharge")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(rechargeBody(990, 99)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.orderNo", not(blankOrNullString())))
				.andExpect(jsonPath("$.data.amountCents").value(990))
				.andExpect(jsonPath("$.data.pointAmount").value(99))
				.andExpect(jsonPath("$.data.status").value("CREATED"));
	}

	@Test
	void orderListIsScopedToCurrentUserAndNewestFirst() throws Exception {
		String token = registerAndExtractToken("order-bob");
		String otherToken = registerAndExtractToken("order-carol");
		createOrder(token, 990, 99);
		createOrder(otherToken, 2990, 299);
		createOrder(token, 1990, 199);

		mockMvc.perform(get("/api/app/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].amountCents").value(1990))
				.andExpect(jsonPath("$.data[1].amountCents").value(990));
	}

	@Test
	void invalidRechargeOrderReturnsBadRequest() throws Exception {
		String token = registerAndExtractToken("order-dan");

		mockMvc.perform(post("/api/app/orders/recharge")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(rechargeBody(0, 99)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	private void createOrder(String token, int amountCents, int pointAmount) throws Exception {
		mockMvc.perform(post("/api/app/orders/recharge")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(rechargeBody(amountCents, pointAmount)))
				.andExpect(status().isOk());
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
