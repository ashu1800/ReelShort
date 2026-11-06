package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelshort.backend.content.account.ReelshortAccountService;

@SpringBootTest
@AutoConfigureMockMvc
class ReelshortAccountControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void clearAccount() {
		jdbcTemplate.update("delete from system_configs where config_key in (?, ?)",
				ReelshortAccountService.EMAIL_KEY, ReelshortAccountService.PASSWORD_KEY);
	}

	@Test
	void accountIsEmptyUntilConfigured() throws Exception {
		String token = adminLogin();

		mockMvc.perform(get("/api/admin/system/reelshort-account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.configured").value(false))
				.andExpect(jsonPath("$.data.emailMasked").value(""))
				.andExpect(jsonPath("$.data.passwordSet").value(false));
	}

	@Test
	void savingAccountStoresItAndNeverEchoesThePassword() throws Exception {
		String token = adminLogin();

		MvcResult result = mockMvc.perform(post("/api/admin/system/reelshort-account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "operator@example.com",
						  "password": "Secret123"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.emailMasked").value("op***@example.com"))
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain("Secret123");
		assertThat(storedValue("content.reelshort.email")).isEqualTo("operator@example.com");
		assertThat(storedValue("content.reelshort.password")).isEqualTo("Secret123");

		// 未配置内容源时保存仍然成功，只是同步结果标记为未登录。
		mockMvc.perform(get("/api/admin/system/reelshort-account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.configured").value(true))
				.andExpect(jsonPath("$.data.passwordSet").value(true))
				.andExpect(jsonPath("$.data.emailMasked").value("op***@example.com"));
	}

	@Test
	void editingOnlyTheEmailKeepsTheStoredPassword() throws Exception {
		String token = adminLogin();
		saveAccount(token, "operator@example.com", "Secret123");

		mockMvc.perform(post("/api/admin/system/reelshort-account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "other@example.com",
						  "password": ""
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.emailMasked").value("ot***@example.com"));

		assertThat(storedValue("content.reelshort.password")).isEqualTo("Secret123");
	}

	@Test
	void accountUpdateRejectsInvalidValues() throws Exception {
		String token = adminLogin();

		mockMvc.perform(post("/api/admin/system/reelshort-account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "not-an-email",
						  "password": "Secret123"
						}
						"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/admin/system/reelshort-account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "operator@example.com",
						  "password": "123"
						}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testEndpointRequiresAConfiguredAccount() throws Exception {
		String token = adminLogin();

		mockMvc.perform(post("/api/admin/system/reelshort-account/test")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", not(containsString("password"))));
	}

	private void saveAccount(String token, String email, String password) throws Exception {
		mockMvc.perform(post("/api/admin/system/reelshort-account")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
				.andExpect(status().isOk());
	}

	private String storedValue(String key) {
		return jdbcTemplate.query("select config_value from system_configs where config_key = ?",
				(rs, rowNum) -> rs.getString(1), key).stream().findFirst().orElse("");
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
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
	}
}
