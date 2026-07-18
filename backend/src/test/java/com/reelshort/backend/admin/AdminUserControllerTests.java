package com.reelshort.backend.admin;

import static org.hamcrest.Matchers.hasSize;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.content.ContentEpisodeRuntimeCache;
import com.reelshort.backend.content.ContentEpisodeRuntimeCacheRepository;
import com.reelshort.backend.user.UserAccount;
import com.reelshort.backend.user.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ContentEpisodeRuntimeCacheRepository runtimeCacheRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void adminCanListUsersWithPointBalance() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-list-alice");

		mockMvc.perform(get("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].username", hasItem(user.username())))
				.andExpect(jsonPath("$.data[*].status", hasItem("ACTIVE")));
	}

	@Test
	void adminCanViewUserDetail() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-detail-alice");
		grantVip(user.userId());
		reportProgress(user.token(), "detail-book", 1, 100, 100);

		mockMvc.perform(get("/api/admin/users/{userId}", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(user.userId().toString()))
				.andExpect(jsonPath("$.data.username").value(user.username()))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.pointBalance").value(1))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(1))
				.andExpect(jsonPath("$.data.walletAddress").doesNotExist())
				.andExpect(jsonPath("$.data.watchRecordCount").value(1))
				.andExpect(jsonPath("$.data.pointRecordCount").value(1))
				.andExpect(jsonPath("$.data.withdrawalRecordCount").value(0));
	}

	@Test
	void adminCanDisableUserAndAppAccessFails() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-disable-alice");

		mockMvc.perform(post("/api/admin/users/{userId}/status", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "status": "DISABLED"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DISABLED"));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(403));
	}

	@Test
	void adminCanQueryOneUsersWatchRecordsAndPointRecords() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser first = registerAppUser("admin-activity-alice");
		RegisteredUser second = registerAppUser("admin-activity-bob");
		grantVip(first.userId());
		grantVip(second.userId());
		reportProgress(first.token(), "first-book", 1, 100, 100);
		reportProgress(second.token(), "second-book", 1, 100, 100);

		mockMvc.perform(get("/api/admin/users/{userId}/watch-records", first.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("first-book"));

		mockMvc.perform(get("/api/admin/users/{userId}/point-records", first.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].source").value("WATCH_REWARD"));
	}

	@Test
	void adminCanAdjustUserPointsAndAuditOperation() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-adjust-alice");

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": 10,
						  "reason": "manual campaign grant",
						  "idempotencyKey": "adjust-admin-can-adjust-1"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pointBalance").value(10));

		mockMvc.perform(get("/api/admin/users/{userId}/point-records", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].amount").value(10))
				.andExpect(jsonPath("$.data[0].source").value("ADMIN_ADJUSTMENT"))
				.andExpect(jsonPath("$.data[0].reason").value("manual campaign grant"));

		mockMvc.perform(get("/api/admin/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].action", hasItem("POINTS_ADJUSTED")));
	}

	@Test
	void adminPointAdjustmentRejectsInvalidAmountAndReason() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-adjust-invalid-alice");

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": 0,
						  "reason": "zero is invalid",
						  "idempotencyKey": "adjust-invalid-zero-1"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": 1,
						  "reason": " ",
						  "idempotencyKey": "adjust-invalid-reason-1"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void adminPointAdjustmentRejectsNegativeBalance() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-adjust-negative-alice");

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": -1,
						  "reason": "manual correction",
						  "idempotencyKey": "adjust-negative-1"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("insufficient point balance"));
	}

	@Test
	void adminCanSubtractUserPointsWhenBalanceIsEnough() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-adjust-subtract-alice");
		adjustPoints(adminToken, user.userId(), 10, "initial grant");

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": -4,
						  "reason": "manual correction",
						  "idempotencyKey": "adjust-subtract-2"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pointBalance").value(6));

		mockMvc.perform(get("/api/admin/users/{userId}/point-records", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].amount").value(-4))
				.andExpect(jsonPath("$.data[0].balanceAfter").value(6));
	}

	@Test
	void adminPointAdjustmentAcceptsMaxLengthReason() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-adjust-long-reason-alice");
		String reason = "a".repeat(255);

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": 1,
						  "reason": "%s",
						  "idempotencyKey": "adjust-long-reason-1"
						}
						""".formatted(reason)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pointBalance").value(1));
	}

	@Test
	void adminStatusChangeWritesAuditLog() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-status-audit-alice");

		mockMvc.perform(post("/api/admin/users/{userId}/status", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "status": "DISABLED"
						}
						"""))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/admin/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].action", hasItem("USER_STATUS_CHANGED")));
	}

	@Test
	void repeatedAdminPointAdjustmentWithSameIdempotencyKeySettlesOnce() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = registerAppUser("admin-adjust-idempotent-alice");
		String body = """
				{
				  "amount": 10,
				  "reason": "exactly once",
				  "idempotencyKey": "adjust-idempotent-1"
				}
				""";

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pointBalance").value(10));
		adjustPoints(adminToken, user.userId(), 5, "later adjustment");
		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pointBalance").value(10));

		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("\"amount\": 10", "\"amount\": 20")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("idempotency key reused with different request"));

		mockMvc.perform(get("/api/admin/users/{userId}/point-records", user.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)));
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

	private RegisteredUser registerAppUser(String username) throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, username);
		return new RegisteredUser(user.userId(), user.token(), user.username());
	}

	private void grantVip(UUID userId) {
		UserAccount user = userAccountRepository.findById(userId).orElseThrow();
		user.grantVip(java.time.OffsetDateTime.now().plusDays(1));
		userAccountRepository.save(user);
	}

	private void reportProgress(String token, String bookId, int episodeNum, int positionSeconds, int durationSeconds)
			throws Exception {
		ContentEpisodeRuntimeCache runtime = runtimeCacheRepository
				.findByBookIdAndEpisodeNumAndChapterId(bookId, episodeNum, "chapter-" + episodeNum)
				.orElseGet(() -> ContentEpisodeRuntimeCache.create(bookId, episodeNum, "chapter-" + episodeNum,
						durationSeconds));
		runtime.update(durationSeconds);
		runtimeCacheRepository.save(runtime);
		mockMvc.perform(post("/api/app/watch/progress")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "bookId": "%s",
						  "bookTitle": "Title %s",
						  "filteredTitle": "filtered-%s",
						  "episodeNum": %d,
						  "chapterId": "chapter-%d",
						  "positionSeconds": %d,
						  "durationSeconds": %d
						}
						""".formatted(bookId, bookId, bookId, episodeNum, episodeNum, positionSeconds, durationSeconds)))
				.andExpect(status().isOk());
	}

	private void adjustPoints(String adminToken, UUID userId, int amount, String reason) throws Exception {
		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", userId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": %d,
						  "reason": "%s",
						  "idempotencyKey": "helper-%s-%d"
						}
						""".formatted(amount, reason, reason.hashCode(), amount)))
				.andExpect(status().isOk());
	}

	private record RegisteredUser(UUID userId, String token, String username) {
	}
}
