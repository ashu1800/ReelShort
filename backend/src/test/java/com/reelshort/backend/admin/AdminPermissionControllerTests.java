package com.reelshort.backend.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import com.reelshort.backend.auth.PasswordHasher;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPermissionControllerTests {

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
	void limitedAdminCanReadUsersButCannotAdjustPoints() throws Exception {
		String adminToken = createLimitedAdminAndLogin();
		RegisteredUser user = registerAppUser("limited-admin-target");

		mockMvc.perform(get("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].username", hasItem(user.username())));

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": 10,
						  "reason": "manual grant"
						}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403))
				.andExpect(jsonPath("$.message").value("forbidden"));
	}

	private String createLimitedAdminAndLogin() throws Exception {
		AdminPermission userRead = adminPermissionRepository.findByCode(AdminPermissions.USER_READ).orElseThrow();
		AdminRole role = AdminRole.create("USER_READER", "User Reader");
		role.grant(userRead);
		adminRoleRepository.saveAndFlush(role);
		AdminUser admin = AdminUser.create("limited-admin", passwordHasher.hash("LimitedAdmin123"),
				AdminUserStatus.ACTIVE);
		admin.assignRole(role);
		adminUserRepository.saveAndFlush(admin);

		MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "limited-admin",
						  "password": "LimitedAdmin123"
						}
						"""))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}

	private RegisteredUser registerAppUser(String username) throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, username);
		return new RegisteredUser(user.userId(), user.token(), user.username());
	}

	private record RegisteredUser(UUID userId, String token, String username) {
	}
}
