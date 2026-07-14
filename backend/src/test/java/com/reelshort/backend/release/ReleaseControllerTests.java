package com.reelshort.backend.release;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reelshort.backend.admin.AdminTokenRepository;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.auth.AccessTokenRepository;
import com.reelshort.backend.auth.TokenHasher;
import com.reelshort.backend.system.web.GlobalExceptionHandler;
import com.reelshort.backend.system.web.RequestIdFilter;

@WebMvcTest(controllers = {ReleaseController.class, InternalReleaseController.class})
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "reelshort.internal.super-token=test-super-token")
class ReleaseControllerTests {

	private static final String SUPER_TOKEN = "test-super-token";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReleaseService releaseService;

	@MockitoBean
	private AccessTokenRepository accessTokenRepository;

	@MockitoBean
	private AdminTokenRepository adminTokenRepository;

	@MockitoBean
	private AdminUserRepository adminUserRepository;

	@MockitoBean
	private TokenHasher tokenHasher;

	@Test
	void latestReturnsManifestWithPresignedUrls() throws Exception {
		UpdateManifestResponse manifest = new UpdateManifestResponse("0.4.2", 6, "ShortLink v0.4.2", "Bug fixes",
				"2026-07-14T10:00:00Z", "https://cos.example.com/signed-apk?token=abc",
				"https://cos.example.com/signed-sha?token=def", 12345L, 65L,
				"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", 1L, false);
		when(releaseService.latestManifest()).thenReturn(Optional.of(manifest));

		mockMvc.perform(get("/api/app/release/latest"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.versionName").value("0.4.2"))
				.andExpect(jsonPath("$.data.versionCode").value(6))
				.andExpect(jsonPath("$.data.apkUrl").value("https://cos.example.com/signed-apk?token=abc"))
				.andExpect(jsonPath("$.data.sha256Url").value("https://cos.example.com/signed-sha?token=def"))
				.andExpect(jsonPath("$.data.sizeBytes").value(12345))
				.andExpect(jsonPath("$.data.apkSha256")
						.value("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"));
	}

	@Test
	void latestReturns404WhenNoRelease() throws Exception {
		when(releaseService.latestManifest()).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/app/release/latest"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("no release available"));
	}

	@Test
	void internalPublishRequiresSuperToken() throws Exception {
		mockMvc.perform(post("/api/internal/release/publish")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPublishBody()))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/internal/release/publish")
				.header("X-Internal-Super-Token", "wrong-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validPublishBody()))
				.andExpect(status().isForbidden());
	}

	private String validPublishBody() {
		return """
				{
				  "versionName": "0.4.3",
				  "versionCode": 7,
				  "apkObjectKey": "releases/android/ShortLink-v0.4.3.apk",
				  "sha256ObjectKey": "releases/android/ShortLink-v0.4.3.apk.sha256",
				  "apkSizeBytes": 20000,
				  "sha256SizeBytes": 65,
				  "apkSha256": "1111111111111111111111111111111111111111111111111111111111111111",
				  "title": "ShortLink v0.4.3",
				  "releaseNotes": "Release",
				  "mandatory": false,
				  "minimumVersionCode": 1
				}
				""";
	}
}
