package com.reelshort.backend.commerce;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = "reelshort.internal.super-token=test-super-token")
@AutoConfigureMockMvc
class WalletWithdrawalTransferControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void withdrawalSummaryReturnsZeroBalanceBeforePointAccountExists() throws Exception {
		RegisteredUser user = createUser("+1", "4155550200", "Password123");

		mockMvc.perform(get("/api/app/withdrawals/summary")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(0))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(0))
				.andExpect(jsonPath("$.data.walletAddress").doesNotExist());
	}

	@Test
	void walletBindRequiresSmsAndBankCardBindingAlwaysFails() throws Exception {
		RegisteredUser user = createUser("+1", "4155550201", "Password123");

		mockMvc.perform(post("/api/app/wallet/verification/send")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "WALLET_BIND"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.expiresInSeconds").value(120));

		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "walletAddress": "TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v1abc",
						  "verificationCode": "000000"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.network").value("TRC20"))
				.andExpect(jsonPath("$.data.walletAddress").value("TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v1abc"));

		mockMvc.perform(post("/api/app/wallet/bank-card")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "holderName": "Alice",
						  "cardNumber": "4111111111111111"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Bank card withdrawal is not supported"));
	}

	@Test
	void withdrawalSubmissionFreezesPointsAndAdminApprovalDeductsFrozenPoints() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = createUser("+1", "4155550202", "Password123");
		adjustPoints(adminToken, user.userId(), 200, "seed withdrawal balance");
		bindWallet(user.token(), "TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v2abc");

		String withdrawalId = JsonPath.read(mockMvc.perform(post("/api/app/withdrawals")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "pointAmount": 120
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.pointAmount").value(120))
				.andExpect(jsonPath("$.data.walletAddress").value("TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v2abc"))
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
				.andExpect(jsonPath("$.data.balance").value(200))
				.andExpect(jsonPath("$.data.frozenPoints").value(120))
				.andExpect(jsonPath("$.data.availablePoints").value(80));

		mockMvc.perform(post("/api/admin/withdrawals/{withdrawalId}/approve", UUID.fromString(withdrawalId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "txHash": "trc-tx-123",
						  "note": "paid manually"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.txHash").value("trc-tx-123"));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(80))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(80));

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].source", hasItem("WITHDRAWAL")));
	}

	@Test
	void adminRejectionReleasesFrozenWithdrawalPoints() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser user = createUser("+1", "4155550203", "Password123");
		adjustPoints(adminToken, user.userId(), 150, "seed withdrawal balance");
		bindWallet(user.token(), "TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v3abc");
		String withdrawalId = JsonPath.read(mockMvc.perform(post("/api/app/withdrawals")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "pointAmount": 100
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

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(150))
				.andExpect(jsonPath("$.data.frozenPoints").value(0))
				.andExpect(jsonPath("$.data.availablePoints").value(150));
	}

	@Test
	void pointTransferMovesAvailablePointsWithoutFeeAndRecordsBothSides() throws Exception {
		String adminToken = adminLogin();
		RegisteredUser sender = createUser("+1", "4155550204", "Password123");
		RegisteredUser receiver = createUser("+44", "2075550204", "Password123");
		adjustPoints(adminToken, sender.userId(), 90, "seed transfer balance");

		mockMvc.perform(post("/api/app/points/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + sender.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "recipientAccount": "+442075550204",
						  "pointAmount": 30
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.pointAmount").value(30))
				.andExpect(jsonPath("$.data.senderAccount").value("+14155550204"))
				.andExpect(jsonPath("$.data.recipientAccount").value("+442075550204"));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + sender.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.availablePoints").value(60));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + receiver.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.availablePoints").value(30));

		mockMvc.perform(get("/api/app/points/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + sender.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].direction").value("OUT"));

		mockMvc.perform(get("/api/admin/users/{userId}/point-transfers", sender.userId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)));
	}

	private RegisteredUser createUser(String countryCode, String phoneNumber, String password) throws Exception {
		String createResponse = mockMvc.perform(post("/api/internal/users/register-phone")
				.header("X-Internal-Super-Token", "test-super-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "countryCode": "%s",
						  "phoneNumber": "%s",
						  "password": "%s"
						}
						""".formatted(countryCode, phoneNumber, password)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		UUID userId = UUID.fromString(JsonPath.read(createResponse, "$.data.userId"));
		String account = JsonPath.read(createResponse, "$.data.phoneE164");
		String token = JsonPath.read(mockMvc.perform(post("/api/app/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "countryCode": "%s",
						  "phoneNumber": "%s",
						  "password": "%s"
						}
						""".formatted(countryCode, phoneNumber, password)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.data.token");
		return new RegisteredUser(userId, token, account);
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
						  "reason": "%s"
						}
						""".formatted(amount, reason)))
				.andExpect(status().isOk());
	}

	private void bindWallet(String token, String walletAddress) throws Exception {
		mockMvc.perform(post("/api/app/wallet/verification/send")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "purpose": "WALLET_BIND"
						}
						"""))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/app/wallet")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "walletAddress": "%s",
						  "verificationCode": "000000"
						}
						""".formatted(walletAddress)))
				.andExpect(status().isOk());
	}

	private record RegisteredUser(UUID userId, String token, String account) {
	}
}
