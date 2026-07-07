package com.reelshort.backend.system;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
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
import com.reelshort.backend.system.config.SystemConfigRegistry;
import com.reelshort.backend.system.config.SystemConfigService;

@SpringBootTest
@AutoConfigureMockMvc
class SystemConfigControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SystemConfigService systemConfigService;

	@AfterEach
	void resetConfigs() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_STAGE_POINTS, "1");
		systemConfigService.update(SystemConfigRegistry.CONTENT_RECOMMENDATION_STRATEGY, "LATEST");
		systemConfigService.update(SystemConfigRegistry.WITHDRAW_MINIMUM_POINTS, "100");
		systemConfigService.update(SystemConfigRegistry.WITHDRAW_USDT_PER_POINT, "0.001");
		systemConfigService.update(SystemConfigRegistry.POINTS_TRANSFER_MINIMUM_POINTS, "1");
	}

	@Test
	void adminCanListDefaultSystemConfigs() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/system/configs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(5)))
				.andExpect(jsonPath("$.data[*].key", hasItem("points.watch.stage-points")))
				.andExpect(jsonPath("$.data[*].key", hasItem("content.recommendation.strategy")))
				.andExpect(jsonPath("$.data[*].key", hasItem("withdraw.minimum-points")))
				.andExpect(jsonPath("$.data[*].key", hasItem("withdraw.usdt-per-point")))
				.andExpect(jsonPath("$.data[*].key", hasItem("points.transfer.minimum-points")));
	}

	@Test
	void adminCanUpdateWatchStagePointsAndAffectsRewards() throws Exception {
		String adminToken = adminLogin();
		String appToken = registerAndExtractAppToken("config-reward-user");

		mockMvc.perform(post("/api/admin/system/configs/{configKey}", "points.watch.stage-points")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "value": "2"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.value").value("2"));

		reportProgress(appToken, "config-book", 1, 25, 100)
				.andExpect(jsonPath("$.data.awardedPoints").value(2));

		mockMvc.perform(get("/api/admin/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].action", hasItem("SYSTEM_CONFIG_UPDATED")));
	}

	@Test
	void zeroWatchStagePointsClaimsStageWithoutBalanceOrTransaction() throws Exception {
		String adminToken = adminLogin();
		String appToken = registerAndExtractAppToken("config-zero-reward-user");

		updateConfig(adminToken, "points.watch.stage-points", "0")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.value").value("0"));

		reportProgress(appToken, "config-zero-book", 1, 25, 100)
				.andExpect(jsonPath("$.data.awardedPoints").value(0))
				.andExpect(jsonPath("$.data.awardedStages", hasSize(1)))
				.andExpect(jsonPath("$.data.awardedStages[0]").value(25));

		mockMvc.perform(get("/api/app/points/account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.balance").value(0));

		mockMvc.perform(get("/api/app/points/records")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));

		updateConfig(adminToken, "points.watch.stage-points", "1")
				.andExpect(status().isOk());

		reportProgress(appToken, "config-zero-book", 1, 25, 100)
				.andExpect(jsonPath("$.data.awardedPoints").value(0))
				.andExpect(jsonPath("$.data.awardedStages", hasSize(0)));
	}

	@Test
	void adminConfigUpdateRejectsUnknownKeyAndInvalidValue() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(post("/api/admin/system/configs/{configKey}", "unknown.key")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "value": "1"
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(404));

		mockMvc.perform(post("/api/admin/system/configs/{configKey}", "points.watch.stage-points")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "value": "-1"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void enumConfigUpdateNormalizesValueAndRejectsUnsupportedValue() throws Exception {
		String adminToken = adminLogin();

		updateConfig(adminToken, "content.recommendation.strategy", "popular")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.value").value("POPULAR"));

		updateConfig(adminToken, "content.recommendation.strategy", "recent")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void appTokenCannotAccessAdminSystemConfigs() throws Exception {
		String appToken = registerAndExtractAppToken("config-boundary-user");

		mockMvc.perform(get("/api/admin/system/configs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	private org.springframework.test.web.servlet.ResultActions reportProgress(String token, String bookId, int episodeNum,
			int positionSeconds, int durationSeconds) throws Exception {
		return mockMvc.perform(post("/api/app/watch/progress")
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

	private org.springframework.test.web.servlet.ResultActions updateConfig(String token, String configKey, String value)
			throws Exception {
		return mockMvc.perform(post("/api/admin/system/configs/{configKey}", configKey)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "value": "%s"
						}
						""".formatted(value)));
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

	private String registerAndExtractAppToken(String username) throws Exception {
		return TestAppUsers.token(mockMvc, objectMapper, username);
	}
}
