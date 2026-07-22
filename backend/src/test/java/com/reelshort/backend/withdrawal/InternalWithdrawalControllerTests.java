package com.reelshort.backend.withdrawal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;

import com.reelshort.backend.admin.AdminTokenRepository;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.auth.AccessTokenRepository;
import com.reelshort.backend.auth.TokenHasher;
import com.reelshort.backend.system.web.GlobalExceptionHandler;
import com.reelshort.backend.system.web.RequestIdFilter;

@WebMvcTest(controllers = InternalWithdrawalController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "reelshort.internal.super-token=test-super-token")
class InternalWithdrawalControllerTests {

	private static final String TOKEN = "test-super-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WithdrawalService withdrawalService;

	@MockitoBean
	private AccessTokenRepository accessTokenRepository;

	@MockitoBean
	private AdminTokenRepository adminTokenRepository;

	@MockitoBean
	private AdminUserRepository adminUserRepository;

	@MockitoBean
	private TokenHasher tokenHasher;

	@Test
	void thresholdsReturnsSnapshotWithSuperToken() throws Exception {
		when(withdrawalService.thresholds()).thenReturn(new WithdrawalConversion.Snapshot(
				3969, "0.14", "0.0028", "10", 10));

		mockMvc.perform(get("/api/internal/withdrawal/thresholds")
				.header("X-Internal-Super-Token", TOKEN))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.minimumPoints").value(3969))
				.andExpect(jsonPath("$.data.usdtPer50Points").value("0.14"))
				.andExpect(jsonPath("$.data.usdtPerPoint").value("0.0028"))
				.andExpect(jsonPath("$.data.minimumUsdt").value("10"));
	}

	@Test
	void thresholdsRejectsMissingToken() throws Exception {
		mockMvc.perform(get("/api/internal/withdrawal/thresholds"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("unauthorized"));
	}

	@Test
	void thresholdsRejectsWrongToken() throws Exception {
		mockMvc.perform(get("/api/internal/withdrawal/thresholds")
				.header("X-Internal-Super-Token", "wrong"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("forbidden"));
	}
}
