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
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.admin.AdminUser;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;
import com.reelshort.backend.system.security.TotpService;

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
	private AdminUserRepository adminUserRepository;

	@Autowired
	private TotpService totpService;

	private static final String TEST_TOTP_SECRET = "JBSWY3DPEHPK3PXP";

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
						  "walletAddress": "%s"
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
	void walletBindRejectsInvalidTrcChecksum() throws Exception {
		RegisteredUser user = createUser("wallet-invalid");

		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "network": "ERC20",
						  "walletAddress": "%s"
						}
						""".formatted(INVALID_ETH_ADDRESS)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("invalid wallet address for ERC20"));
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
						  "pointAmount": 3600
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.pointAmount").value(3600))
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
				.andExpect(jsonPath("$.data.frozenPoints").value(3600))
				.andExpect(jsonPath("$.data.availablePoints").value(400));

		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/approve", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "txHash": "trc-tx-123",
						  "note": "paid manually",
						  "totpCode": "%s"
						}
						""".formatted(validTotpCode())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.txHash").value("trc-tx-123"));

		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/approve", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "txHash": "trc-tx-duplicate",
						  "note": "duplicate",
						  "totpCode": "%s"
						}
						""".formatted(validTotpCode())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("withdrawal is not pending"));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(400))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(400));

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].source", hasItem("WITHDRAWAL")));
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
						  "pointAmount": 3600
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
	void withdrawalUsdtAmountIsRoundedToStorageScale() throws Exception {
		String adminToken = adminLogin();
		try {
			systemConfigService.update(SystemConfigRegistry.WITHDRAW_CNY_PER_POINT, "0.12345678");
			systemConfigService.update(SystemConfigRegistry.WITHDRAW_CNY_PER_USD, "1");
			systemConfigService.update(SystemConfigRegistry.WITHDRAW_MINIMUM_USD, "1");
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
				// usdt = 90 * 0.12345678 / 1 = 11.111110 → stripTrailingZeros → 11.11111
				.andExpect(jsonPath("$.data.usdtAmount").value("11.11111"))
					.andExpect(jsonPath("$.data.usdtPerPoint").value("0.12345678"));
		}
		finally {
			systemConfigService.update(SystemConfigRegistry.WITHDRAW_CNY_PER_POINT, "0.02");
			systemConfigService.update(SystemConfigRegistry.WITHDRAW_CNY_PER_USD, "7.2");
			systemConfigService.update(SystemConfigRegistry.WITHDRAW_MINIMUM_USD, "10");
		}
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

	/** H1: 单笔提现现在需要 2FA，测试中为 admin 启用 TOTP 并生成验证码 */
	private String validTotpCode() {
		AdminUser admin = adminUserRepository.findByUsername("admin").orElseThrow();
		if (!admin.totpEnabled()) {
			admin.enableTotp(TEST_TOTP_SECRET);
			adminUserRepository.save(admin);
		}
		return totpService.generateCurrentCode(TEST_TOTP_SECRET);
	}

	private void adjustPoints(String adminToken, UUID userId, int amount, String reason) throws Exception {
		mockMvc.perform(post("/api/admin/users/{userId}/points/adjust", userId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "amount": %d,
						  "reason": "%s"
						}
						""".formatted(amount, reason)))
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
						  "walletAddress": "%s"
						}
						""".formatted(network, walletAddress)))
				.andExpect(status().isOk());
	}

	private record RegisteredUser(UUID userId, String token, String account) {
	}
}
