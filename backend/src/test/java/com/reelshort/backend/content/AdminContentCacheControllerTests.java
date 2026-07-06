package com.reelshort.backend.content;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AdminContentCacheControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ContentShelfCacheRepository contentShelfCacheRepository;

	@Autowired
	private ContentBookCacheRepository contentBookCacheRepository;

	@Autowired
	private ContentEpisodeCacheRepository contentEpisodeCacheRepository;

	@Autowired
	private ContentRefreshRunRepository contentRefreshRunRepository;

	@MockitoBean
	private ContentProvider contentProvider;

	@BeforeEach
	void cleanCache() {
		contentRefreshRunRepository.deleteAll();
		contentShelfCacheRepository.deleteAll();
		contentBookCacheRepository.deleteAll();
		contentEpisodeCacheRepository.deleteAll();
	}

	@Test
	void adminCanReadContentCacheStatus() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/content/cache")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.bookCount").value(0))
				.andExpect(jsonPath("$.data.episodeCacheCount").value(0))
				.andExpect(jsonPath("$.data.videoCacheCount").value(0))
				.andExpect(jsonPath("$.data.shelves", hasSize(6)))
				.andExpect(jsonPath("$.data.shelves[*].shelfType", hasItem("recommend")))
				.andExpect(jsonPath("$.data.shelves[*].shelfType", hasItem("new-release")))
				.andExpect(jsonPath("$.data.shelves[*].shelfType", hasItem("drama-dub")))
				.andExpect(jsonPath("$.data.shelves[*].locale", hasItem("en")))
				.andExpect(jsonPath("$.data.shelves[*].locale", hasItem("zh-TW")))
				.andExpect(jsonPath("$.data.shelves[*].health", hasItem("MISSING")))
				.andExpect(jsonPath("$.data.shelves[*].healthMessage", hasItem("not refreshed yet")))
				.andExpect(jsonPath("$.data.recentRefreshRuns", hasSize(0)));
	}

	@Test
	void adminCanRefreshShelfAndAuditIsRecorded() throws Exception {
		String adminToken = adminLogin();
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH)).thenReturn(List.of(
				new ContentBook("book-refresh", "Refresh", "refresh", "https://example.com/refresh.jpg", "", 4)));

		mockMvc.perform(post("/api/admin/content/cache/shelves/recommend/refresh")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("book-refresh"));

		mockMvc.perform(get("/api/admin/content/cache")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookCount").value(1))
				.andExpect(jsonPath("$.data.recentRefreshRuns", hasSize(1)))
				.andExpect(jsonPath("$.data.recentRefreshRuns[0].triggerSource").value("ADMIN"))
				.andExpect(jsonPath("$.data.recentRefreshRuns[0].status").value("SUCCESS"))
				.andExpect(jsonPath("$.data.recentRefreshRuns[0].itemCount").value(1));

		mockMvc.perform(get("/api/admin/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].action", hasItem("CONTENT_CACHE_REFRESHED")));
	}

	@Test
	void adminCanRefreshTraditionalChineseShelf() throws Exception {
		String adminToken = adminLogin();
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.TRADITIONAL_CHINESE)).thenReturn(List.of(
				new ContentBook("book-zh", "繁中短劇", "zh-drama", "https://example.com/zh.jpg", "", 8)));

		mockMvc.perform(post("/api/admin/content/cache/shelves/recommend/refresh")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("locale", "zh-TW"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].bookId").value("book-zh"));

		mockMvc.perform(get("/api/admin/content/cache")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recentRefreshRuns[0].locale").value("zh-TW"));
	}

	@Test
	void adminCanRefreshShelfForAllSupportedLocalesWithPartialFailure() throws Exception {
		String adminToken = adminLogin();
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH)).thenReturn(List.of(
				new ContentBook("book-en", "English Drama", "english-drama", "https://example.com/en.jpg", "", 6)));
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.TRADITIONAL_CHINESE))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		mockMvc.perform(post("/api/admin/content/cache/shelves/recommend/refresh-locales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[*].locale", hasItem("en")))
				.andExpect(jsonPath("$.data[*].locale", hasItem("zh-TW")))
				.andExpect(jsonPath("$.data[*].status", hasItem("SUCCESS")))
				.andExpect(jsonPath("$.data[*].status", hasItem("FAILED")))
				.andExpect(jsonPath("$.data[*].itemCount", hasItem(1)))
				.andExpect(jsonPath("$.data[*].errorMessage", hasItem("content provider unavailable")));

		mockMvc.perform(get("/api/admin/content/cache")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recentRefreshRuns", hasSize(2)))
				.andExpect(jsonPath("$.data.recentRefreshRuns[*].status", hasItem("SUCCESS")))
				.andExpect(jsonPath("$.data.recentRefreshRuns[*].status", hasItem("FAILED")));

		mockMvc.perform(get("/api/admin/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].action", hasItem("CONTENT_CACHE_REFRESHED_LOCALES")));
	}

	@Test
	void contentCacheStatusIncludesEpisodeCacheCount() throws Exception {
		String adminToken = adminLogin();
		String appToken = registerAndExtractAppToken("episode-cache-user");
		when(contentProvider.getEpisodesDetail("book-episodes", "episodes", ContentLocale.ENGLISH))
				.thenReturn(new ContentEpisodesDetail(java.util.Optional.empty(),
						List.of(new ContentEpisode(1, "chapter-1", "", ""))));

		mockMvc.perform(get("/api/app/content/books/book-episodes/episodes")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken)
				.param("filteredTitle", "episodes"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/admin/content/cache")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.episodeCacheCount").value(1));
	}

	@Test
	void adminRefreshRejectsUnknownShelf() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(post("/api/admin/content/cache/shelves/unknown/refresh")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void appTokenCannotAccessAdminContentCache() throws Exception {
		String appToken = registerAndExtractAppToken("cache-boundary-user");

		mockMvc.perform(get("/api/admin/content/cache")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
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
		MvcResult result = mockMvc.perform(post("/api/app/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "%s",
						  "password": "Password123"
						}
						""".formatted(username)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		return response.path("data").path("token").asText();
	}
}
