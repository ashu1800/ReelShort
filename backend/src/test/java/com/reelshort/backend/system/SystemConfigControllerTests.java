package com.reelshort.backend.system;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.TestAppUsers;
import com.reelshort.backend.points.PointsService;
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

	@Autowired
	private PointsService pointsService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void resetConfigs() {
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "60");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "35");
		systemConfigService.update(SystemConfigRegistry.CONTENT_RECOMMENDATION_STRATEGY, "LATEST");
		systemConfigService.update(SystemConfigRegistry.WITHDRAW_CNY_PER_POINT, "0.02");
		systemConfigService.update(SystemConfigRegistry.WITHDRAW_CNY_PER_USD, "7.2");
		systemConfigService.update(SystemConfigRegistry.WITHDRAW_MINIMUM_USD, "10");
		systemConfigService.update(SystemConfigRegistry.POINTS_TRANSFER_MINIMUM_POINTS, "1");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		jdbcTemplate.update("delete from point_daily_earning_quotas");
		jdbcTemplate.update("delete from point_daily_earning_rules");
	}

	@Test
	void adminCanListDefaultSystemConfigs() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/system/configs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(8)))
				.andExpect(jsonPath("$.data[*].key", hasItem("points.watch.seconds-per-point")))
				.andExpect(jsonPath("$.data[*].key", hasItem("points.daily-earned.maximum")))
				.andExpect(jsonPath("$.data[*].key", hasItem("points.daily-earned.fluctuation-percent")))
				.andExpect(jsonPath("$.data[*].key", hasItem("content.recommendation.strategy")))
				.andExpect(jsonPath("$.data[*].key", hasItem("withdraw.cny-per-point")))
				.andExpect(jsonPath("$.data[*].key", hasItem("withdraw.cny-per-usd")))
				.andExpect(jsonPath("$.data[*].key", hasItem("withdraw.minimum-usd")))
				.andExpect(jsonPath("$.data[*].key", hasItem("points.transfer.minimum-points")));
	}

	@Test
	void adminCanUpdateWatchSecondsPerPoint() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(post("/api/admin/system/configs/{configKey}", "points.watch.seconds-per-point")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "value": "30"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.value").value("30"));

		mockMvc.perform(get("/api/admin/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].action", hasItem("SYSTEM_CONFIG_UPDATED")));
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

		mockMvc.perform(post("/api/admin/system/configs/{configKey}", "points.watch.seconds-per-point")
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
	void adminCanUpdateDailyEarnedMaximum() throws Exception {
		String adminToken = adminLogin();

		updateConfig(adminToken, "points.daily-earned.maximum", "250")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.value").value("250"));

		updateConfig(adminToken, "points.daily-earned.maximum", "-1")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void withdrawalConversionConfigsRejectInvalidValues() throws Exception {
		String adminToken = adminLogin();

		updateConfig(adminToken, "withdraw.cny-per-point", "0.123456789")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));

		updateConfig(adminToken, "withdraw.cny-per-usd", "1001")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));

		updateConfig(adminToken, "withdraw.cny-per-point", "0.12345678")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.value").value("0.12345678"));
	}

	@Test
	void adminDailyLimitChangesApplyStartingWithTheNextServerDay() throws Exception {
		String adminToken = adminLogin();
		systemConfigService.update(SystemConfigRegistry.POINTS_WATCH_SECONDS_PER_POINT, "1");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_MAXIMUM, "1000");
		systemConfigService.update(SystemConfigRegistry.POINTS_DAILY_EARNED_FLUCTUATION_PERCENT, "0");

		updateConfig(adminToken, "points.daily-earned.maximum", "250")
				.andExpect(status().isOk());

		assertThat(pointsService.awardWatchProgress(java.util.UUID.randomUUID(), "admin-snapshot-book", 1, 100, 1000)
				.awardedPoints()).isEqualTo(1000);
	}

	@Test
	void withdrawalConversionRejectsACombinationThatOverflowsMinimumPoints() throws Exception {
		String adminToken = adminLogin();

		updateConfig(adminToken, "withdraw.cny-per-point", "0.00000001")
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
