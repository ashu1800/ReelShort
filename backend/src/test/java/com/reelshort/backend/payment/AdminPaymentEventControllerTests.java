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
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.admin.AdminPermission;
import com.reelshort.backend.admin.AdminPermissionRepository;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.AdminRole;
import com.reelshort.backend.admin.AdminRoleRepository;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.admin.AdminUserStatus;
import com.reelshort.backend.auth.PasswordHasher;
import com.reelshort.backend.order.CreateRechargeOrderRequest;
import com.reelshort.backend.order.RechargeOrderResponse;
import com.reelshort.backend.order.RechargeOrderService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPaymentEventControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PaymentCallbackService paymentCallbackService;

	@Autowired
	private PaymentEventRepository paymentEventRepository;

	@Autowired
	private RechargeOrderService rechargeOrderService;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private AdminRoleRepository adminRoleRepository;

	@Autowired
	private AdminPermissionRepository adminPermissionRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	@Test
	void paymentEventsRequireAdminToken() throws Exception {
		mockMvc.perform(get("/api/admin/payments/events"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void paymentEventsRequirePaymentEventReadPermission() throws Exception {
		String adminToken = createLimitedAdminAndLogin();

		mockMvc.perform(get("/api/admin/payments/events")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403));
	}

	@Test
	void adminCanListPaymentEventsNewestFirst() throws Exception {
		String adminToken = adminLogin();
		createProcessedEvent("evt-admin-list-old", 990, 99, "mock-pay");
		createProcessedEvent("evt-admin-list-new", 1990, 199, "stripe");

		mockMvc.perform(get("/api/admin/payments/events")
				.param("paymentChannel", "stripe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].providerEventId").value("evt-admin-list-new"))
				.andExpect(jsonPath("$.data[0].paymentChannel").value("stripe"))
				.andExpect(jsonPath("$.data[0].amountCents").value(1990))
				.andExpect(jsonPath("$.data[0].status").value("PROCESSED"));
	}

	@Test
	void adminCanFilterPaymentEventsByStatusOrderNoAndChannel() throws Exception {
		String adminToken = adminLogin();
		RechargeOrderResponse target = createProcessedEvent("evt-admin-filter-target", 990, 99, "mock-pay");
		createProcessedEvent("evt-admin-filter-other", 1990, 199, "stripe");
		String rejectedOrderNo = "RO-missing-admin-filter";
		createRejectedEvent("evt-admin-filter-rejected", rejectedOrderNo, "mock-pay");

		mockMvc.perform(get("/api/admin/payments/events")
				.param("status", "PROCESSED")
				.param("orderNo", target.orderNo())
				.param("paymentChannel", "mock-pay")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].providerEventId").value("evt-admin-filter-target"))
				.andExpect(jsonPath("$.data[0].orderNo").value(target.orderNo()));

		mockMvc.perform(get("/api/admin/payments/events")
				.param("status", "REJECTED")
				.param("orderNo", rejectedOrderNo)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].providerEventId").value("evt-admin-filter-rejected"))
				.andExpect(jsonPath("$.data[0].failureReason").value("order not found"));
	}

	private RechargeOrderResponse createProcessedEvent(String eventId, int amountCents, int pointAmount,
			String paymentChannel) {
		RechargeOrderResponse order = rechargeOrderService.create(java.util.UUID.randomUUID(),
				new CreateRechargeOrderRequest(amountCents, pointAmount));
		paymentCallbackService.handle(new PaymentCallbackRequest(eventId, order.orderNo(), paymentChannel,
				amountCents));
		return order;
	}

	private void createRejectedEvent(String eventId, String orderNo, String paymentChannel) {
		paymentEventRepository.saveAndFlush(PaymentEvent.rejected(new PaymentCallbackRequest(eventId, orderNo,
				paymentChannel, 990), "order not found"));
	}

	private String adminLogin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "admin",
						  "password": "Admin123"
						}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}

	private String createLimitedAdminAndLogin() throws Exception {
		AdminPermission userRead = adminPermissionRepository.findByCode(AdminPermissions.USER_READ).orElseThrow();
		AdminRole role = AdminRole.create("PAYMENT_EVENT_LIMITED_READER", "Payment Event Limited Reader");
		role.grant(userRead);
		adminRoleRepository.saveAndFlush(role);
		AdminUser admin = AdminUser.create("payment-event-limited-admin",
				passwordHasher.hash("LimitedAdmin123"), AdminUserStatus.ACTIVE);
		admin.assignRole(role);
		adminUserRepository.saveAndFlush(admin);

		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "payment-event-limited-admin",
						  "password": "LimitedAdmin123"
						}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}
}
