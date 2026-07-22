package com.reelshort.backend.commerce;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.admin.AdminAuditLogRepository;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;

@SpringBootTest
@AutoConfigureMockMvc
class WalletWithdrawalTransferControllerTests {

	private static final String VALID_ETH_ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb1";
	private static final String INVALID_ETH_ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SystemConfigService systemConfigService;

	@Autowired
	private AdminAuditLogRepository adminAuditLogRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void withdrawalSummaryReturnsZeroBalanceBeforePointAccountExists() throws Exception {
		RegisteredUser user = createUser("wallet-summary");

		mockMvc.perform(get("/api/app/withdrawals/summary")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(0))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(0))
				.andExpect(jsonPath("$.data.walletAddress").doesNotExist());
	}

	@Test
	void walletBindSucceedsWithoutSmsAndBankCardAlwaysFailsFaceVerification() throws Exception {
		RegisteredUser user = createUser("wallet-bind");

		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "network": "ERC20",
						  "walletAddress": "%s",
						  "password": "Password123"
						}
						""".formatted(VALID_ETH_ADDRESS)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.network").value("ERC20"))
				.andExpect(jsonPath("$.data.walletAddress").value(VALID_ETH_ADDRESS));

		mockMvc.perform(post("/api/app/wallet/bank-card")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "holderName": "Alice",
						  "cardNumber": "4111111111111111",
						  "expiryMonth": "12",
						  "expiryYear": "30",
						  "cvv": "123"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value("face verification failed"));
	}

	@Test
	void walletBindRejectsTronNetworkAfterErc20OnlyCutover() throws Exception {
		RegisteredUser user = createUser("wallet-tron-disabled");

		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "network": "TRC20",
						  "walletAddress": "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("only ERC20 wallets are supported"));
	}

	@Test
	void walletBindRejectsInvalidTrcChecksum() throws Exception {
		RegisteredUser user = createUser("wallet-invalid");

		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "network": "ERC20",
						  "walletAddress": "%s",
						  "password": "Password123"
						}
						""".formatted(INVALID_ETH_ADDRESS)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid wallet address for ERC20"));
	}

	@Test
	void manualErc20ConfirmationApprovesWithdrawalWithoutSecondFactorAndConsumesFrozenPoints() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = createUser("manual-erc20-confirm");
		adjustPoints(adminToken, user.userId(), 4000, "seed manual payout");
		bindWallet(user.token(), VALID_ETH_ADDRESS);
		String withdrawalId = JsonPath.read(mockMvc.perform(post("/api/app/withdrawals")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pointAmount\":3969}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/manual-confirm", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.payoutStatus").value("MANUAL_CONFIRMED"));

		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/manual-confirm", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"));

		mockMvc.perform(get("/api/app/withdrawals/summary")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(31))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(31));
	}

	@Test
	void manualErc20ConfirmationStillRequiresAnAuthenticatedAdminSession() throws Exception {
		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/manual-confirm", UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void withdrawalStatsUsesPresetRangeAndRejectsUnsupportedRange() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/withdrawals/stats")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("range", "THIS_MONTH"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.range").value("THIS_MONTH"))
				.andExpect(jsonPath("$.data.payoutCount").isNumber())
				.andExpect(jsonPath("$.data.totalUsdt").isString());

		mockMvc.perform(get("/api/admin/withdrawals/stats")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("range", "YEAR_TO_DATE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("unsupported withdrawal stats range"));
	}

	@Test
	void withdrawalSubmissionFreezesPointsAndAdminApprovalDeductsFrozenPoints() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = createUser("wallet-withdraw");
		adjustPoints(adminToken, user.userId(), 4000, "seed withdrawal balance");
		bindWallet(user.token(), VALID_ETH_ADDRESS);

		String withdrawalId = JsonPath.read(mockMvc.perform(post("/api/app/withdrawals")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "pointAmount": 3969
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.pointAmount").value(3969))
				.andExpect(jsonPath("$.data.walletAddress").value(VALID_ETH_ADDRESS))
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/admin/withdrawals")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].userAccount").value(user.account()));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(4000))
				.andExpect(jsonPath("$.data.frozenPoints").value(3969))
				.andExpect(jsonPath("$.data.availablePoints").value(31));

		// 自动打款接口已停用，确认不再接收私钥或触发链上广播。
		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/approve", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.message").value("automatic payout is disabled"));

		// 验证 reject 释放冻结积分（这条路径不涉及链上操作）
		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/reject", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "reason": "manual reject for testing"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("REJECTED"));

		org.assertj.core.api.Assertions.assertThat(adminAuditLogRepository.findAll())
				.anySatisfy(audit -> {
					org.assertj.core.api.Assertions.assertThat(audit.action()).isEqualTo("WITHDRAWAL_REJECTED");
					org.assertj.core.api.Assertions.assertThat(audit.targetId()).isEqualTo(UUID.fromString(withdrawalId));
					org.assertj.core.api.Assertions.assertThat(audit.summary())
							.contains("network=ERC20", "status=REJECTED")
							.doesNotContain("private", "secret");
				});

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(4000))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(4000));

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].source", hasItem("ADMIN_ADJUSTMENT")));
	}

	@Test
	void adminRejectionReleasesFrozenWithdrawalPoints() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = createUser("wallet-reject");
		adjustPoints(adminToken, user.userId(), 4000, "seed withdrawal balance");
		bindWallet(user.token(), VALID_ETH_ADDRESS);
		String withdrawalId = JsonPath.read(mockMvc.perform(post("/api/app/withdrawals")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "pointAmount": 3969
						}
						"""))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.data.id");

		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/reject", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "reason": "invalid wallet"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("REJECTED"));

		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/reject", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "reason": "duplicate reject"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("withdrawal is not pending"));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(4000))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(4000));
	}

	@Test
	void withdrawalUsdtAmountIsRoundedDownToCents() throws Exception {
		String adminToken = adminLogin();
		try {
			systemConfigService.update(SystemConfigRegistry.WITHDRAW_USDT_PER_50_POINTS, "6.22955556");
			RegisteredUser user = createUser("wallet-rounding");
			adjustPoints(adminToken, user.userId(), 200, "seed withdrawal balance");
			bindWallet(user.token(), VALID_ETH_ADDRESS);

			mockMvc.perform(post("/api/app/withdrawals")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "pointAmount": 101
							}
							"""))
					.andExpect(status().isOk())
				// pointAmount=101, fee=ceil(101*10/100)=11, withdrawable=90
				// usdt = 90 * 6.22955556 / 50 = 11.213200008 → down to 11.21
				.andExpect(jsonPath("$.data.usdtAmount").value("11.21"))
					.andExpect(jsonPath("$.data.usdtPer50Points").value("6.22955556"));

			org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
					"select usdt_per_50_points from withdrawal_requests where user_id = ?",
					java.math.BigDecimal.class, user.userId()))
					.isEqualByComparingTo("6.22955556");
		}
		finally {
			systemConfigService.update(SystemConfigRegistry.WITHDRAW_USDT_PER_50_POINTS, "0.14");
		}
	}

	@Test
	void walletBindRequiresCurrentPasswordAndRejectsErc20ZeroAddress() throws Exception {
		RegisteredUser user = createUser("wallet-password");

		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "network": "ERC20",
						  "walletAddress": "%s",
						  "password": "WrongPassword"
						}
						""".formatted(VALID_ETH_ADDRESS)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("invalid current password"));

		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "network": "ERC20",
						  "walletAddress": "0x0000000000000000000000000000000000000000",
						  "password": "Password123"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid wallet address for ERC20"));
	}

	@Test
	void walletUnbindRequiresCurrentPassword() throws Exception {
		RegisteredUser user = createUser("wallet-unbind-password");
		bindWallet(user.token(), VALID_ETH_ADDRESS);

		mockMvc.perform(post("/api/app/wallet/unbind")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"WrongPassword\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("invalid current password"));

		mockMvc.perform(post("/api/app/wallet/unbind")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"Password123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.walletAddress").doesNotExist());
	}

	@Test
	void automaticPayoutEndpointsAreGone() throws Exception {
		String adminToken = adminLogin();
		UUID withdrawalId = UUID.randomUUID();

		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/approve", withdrawalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.message").value("automatic payout is disabled"));

		mockMvc.perform(post("/api/admin/withdrawals/batch-approve")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.message").value("automatic payout is disabled"));
	}

	private RegisteredUser createUser(String seed) throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, seed, "Password123");
		return new RegisteredUser(user.userId(), user.token(), user.username());
	}

	private String adminLogin() throws Exception {
		return JsonPath.read(mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "admin",
						  "password": "Admin123"
						}
						"""))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.data.token");
	}

	private void adjustPoints(String adminToken, UUID userId, int amount, String reason) throws Exception {
		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", userId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": %d,
						  "reason": "%s",
						  "idempotencyKey": "commerce-%s-%d"
						}
						""".formatted(amount, reason, reason.hashCode(), amount)))
				.andExpect(status().isOk());
	}

	private void bindWallet(String token, String walletAddress) throws Exception {
		bindWallet(token, "ERC20", walletAddress);
	}

	private void bindWallet(String token, String network, String walletAddress) throws Exception {
		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "network": "%s",
						  "walletAddress": "%s",
						  "password": "Password123"
						}
						""".formatted(network, walletAddress)))
				.andExpect(status().isOk());
	}

	private record RegisteredUser(UUID userId, String token, String account) {
	}
}
