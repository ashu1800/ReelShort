package com.reelshort.backend.system;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SystemApiContractTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthUsesUnifiedSuccessEnvelope() throws Exception {
		mockMvc.perform(get("/api/system/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.message").value("success"))
				.andExpect(jsonPath("$.data.status").value("UP"))
				.andExpect(jsonPath("$.data.service").value("reelshort-backend"))
				.andExpect(jsonPath("$.requestId", not(blankOrNullString())))
				.andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
	}

	@Test
	void unknownRouteUsesUnifiedErrorEnvelope() throws Exception {
		mockMvc.perform(get("/api/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(404))
				.andExpect(jsonPath("$.message", not(blankOrNullString())))
				.andExpect(jsonPath("$.path").value("/api/not-found"))
				.andExpect(jsonPath("$.requestId", not(blankOrNullString())))
				.andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
	}

	@Test
	void protectedAppApiWithoutTokenUsesUnauthorizedEnvelope() throws Exception {
		mockMvc.perform(get("/api/app/content/search"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401))
				.andExpect(jsonPath("$.message", not(blankOrNullString())))
				.andExpect(jsonPath("$.path").value("/api/app/content/search"))
				.andExpect(jsonPath("$.requestId", not(blankOrNullString())))
				.andExpect(jsonPath("$.timestamp", not(blankOrNullString())));
	}
}
