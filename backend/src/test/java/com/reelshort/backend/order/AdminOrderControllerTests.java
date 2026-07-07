package com.reelshort.backend.order;

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
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.admin.AdminPermission;
import com.reelshort.backend.admin.AdminPermissionRepository;
import com.reelshort.backend.admin.AdminPermissions;
import com.reelshort.backend.admin.AdminRole;
import com.reelshort.backend.admin.AdminRoleRepository;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.admin.AdminUserStatus;
import com.reelshort.backend.auth.PasswordHasher;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminOrderControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@Autowired
	private AdminRoleRepository adminRoleRepository;

	@Autowired
	private AdminPermissionRepository adminPermissionRepository;

	@Autowired
	private PasswordHasher passwordHasher;

	@Test
	void adminCanListAllRechargeOrdersNewestFirst() throws Exception {
		String adminToken = adminLogin();
		String aliceToken = registerAndExtractToken("admin-order-alice");
		String bobToken = registerAndExtractToken("admin-order-bob");
		createOrder(aliceToken, 990, 99);
		createOrder(bobToken, 2990, 299);

		mockMvc.perform(get("/api/admin/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].amountCents").value(2990))
				.andExpect(jsonPath("$.data[0].pointAmount").value(299))
				.andExpect(jsonPath("$.data[0].status").value("CREATED"))
				.andExpect(jsonPath("$.data[1].amountCents").value(990));
	}

	@Test
	void adminOrdersRequiresOrderReadPermission() throws Exception {
		String adminToken = createLimitedAdminAndLogin();

		mockMvc.perform(get("/api/admin/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403))
				.andExpect(jsonPath("$.message").value("forbidden"));
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
		AdminRole role = AdminRole.create("ORDER_LIMITED_READER", "Order Limited Reader");
		role.grant(userRead);
		adminRoleRepository.saveAndFlush(role);
		AdminUser admin = AdminUser.create("order-limited-admin", passwordHasher.hash("LimitedAdmin123"),
				AdminUserStatus.ACTIVE);
		admin.assignRole(role);
		adminUserRepository.saveAndFlush(admin);

		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "order-limited-admin",
						  "password": "LimitedAdmin123"
						}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}

	private String registerAndExtractToken(String username) throws Exception {
		return TestAppUsers.token(mockMvc, objectMapper, username);
	}

	private void createOrder(String token, int amountCents, int pointAmount) throws Exception {
		mockMvc.perform(post("/api/app/orders/recharge")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amountCents": %d,
						  "pointAmount": %d
						}
						""".formatted(amountCents, pointAmount)))
				.andExpect(status().isOk());
	}
}
