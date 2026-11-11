package com.reelshort.backend.content;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.reelshort.backend.TestAppUsers;

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
	private ContentVideoCacheRepository contentVideoCacheRepository;

	@Autowired
	private ContentRefreshRunRepository contentRefreshRunRepository;

	@Autowired
	private ContentLibraryCrawlStateRepository contentLibraryCrawlStateRepository;

	@MockitoBean
	private ContentProvider contentProvider;

	@BeforeEach
	void cleanCache() {
		contentRefreshRunRepository.deleteAll();
		contentShelfCacheRepository.deleteAll();
		contentBookCacheRepository.deleteAll();
		contentEpisodeCacheRepository.deleteAll();
		contentVideoCacheRepository.deleteAll();
		contentLibraryCrawlStateRepository.deleteAll();
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
				.andExpect(jsonPath("$.data.shelves", hasSize(3)))
				.andExpect(jsonPath("$.data.shelves[*].shelfType", hasItem("recommend")))
				.andExpect(jsonPath("$.data.shelves[*].shelfType", hasItem("new-release")))
				.andExpect(jsonPath("$.data.shelves[*].shelfType", hasItem("drama-dub")))
				.andExpect(jsonPath("$.data.shelves[*].locale", hasItem("en")))
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
	void adminCanRefreshShelfForAllSupportedLocales() throws Exception {
		String adminToken = adminLogin();
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH)).thenReturn(List.of(
				new ContentBook("book-en", "English Drama", "english-drama", "https://example.com/en.jpg", "", 6)));

		mockMvc.perform(post("/api/admin/content/cache/shelves/recommend/refresh-locales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[*].locale", hasItem("en")))
				.andExpect(jsonPath("$.data[*].status", hasItem("SUCCESS")))
				.andExpect(jsonPath("$.data[*].itemCount", hasItem(1)));

		mockMvc.perform(get("/api/admin/content/cache")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recentRefreshRuns", hasSize(1)))
				.andExpect(jsonPath("$.data.recentRefreshRuns[*].status", hasItem("SUCCESS")));

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

	@Test
	void adminCanReadShelfBooksEpisodesAndProbeVideo() throws Exception {
		String adminToken = adminLogin();
		when(contentProvider.getShelf(ContentShelfType.RECOMMEND, ContentLocale.ENGLISH)).thenReturn(List.of(
				new ContentBook("book-probe", "Probe Drama", "probe-drama", "https://example.com/cover.jpg", "Drama description", 10)));
		when(contentProvider.getEpisodesDetail("book-probe", "probe-drama", ContentLocale.ENGLISH)).thenReturn(
				new ContentEpisodesDetail(
						java.util.Optional.empty(),
						List.of(new ContentEpisode(1, "chap-1", "Episode 1", "Desc 1"))));
		when(contentProvider.getVideoUrl("book-probe", 1, "probe-drama", "chap-1", ContentLocale.ENGLISH)).thenReturn(
				new ContentVideo("https://example.com/video.mp4", 1, 120, null));

		// 1. Read shelf books
		mockMvc.perform(get("/api/admin/content/cache/shelves/recommend/books")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("book-probe"))
				.andExpect(jsonPath("$.data[0].title").value("Probe Drama"));

		// 2. Read episodes for the book
		mockMvc.perform(get("/api/admin/content/cache/books/book-probe/episodes")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].episode").value(1))
				.andExpect(jsonPath("$.data[0].chapterId").value("chap-1"));

		// 3. Video probe for episode
		mockMvc.perform(get("/api/admin/content/cache/books/book-probe/episodes/1/video-probe")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.videoUrl").value("https://example.com/video.mp4"))
				.andExpect(jsonPath("$.data.duration").value(120));
	}

	@Test
	void adminCanReadContentLibraryStatus() throws Exception {
		String adminToken = adminLogin();

		mockMvc.perform(get("/api/admin/content/library")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.bookCount").value(0))
				.andExpect(jsonPath("$.data.episodeCacheCount").value(0))
				.andExpect(jsonPath("$.data.videoCacheCount").value(0))
				.andExpect(jsonPath("$.data.nextPage").value(1))
				.andExpect(jsonPath("$.data.totalPages").value(176))
				.andExpect(jsonPath("$.data.lastAdded").value(0))
				.andExpect(jsonPath("$.data.active").value(false));
	}

	@Test
	void adminCanBrowseContentLibraryWithPagingAndSearch() throws Exception {
		String adminToken = adminLogin();
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(
				new ContentBook("lib-love", "Love Story", "love-story", "https://example.com/love.jpg",
						"A romantic drama.", 12)));
		contentBookCacheRepository.saveAndFlush(ContentBookCache.from(
				new ContentBook("lib-revenge", "Revenge", "revenge", "https://example.com/revenge.jpg",
						"Dark secrets.", 8)));

		mockMvc.perform(get("/api/admin/content/library/books")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("page", "0")
				.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.total").value(2))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(1))
				.andExpect(jsonPath("$.data.books", hasSize(1)));

		mockMvc.perform(get("/api/admin/content/library/books")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("query", "LOVE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.books[0].bookId").value("lib-love"));

		mockMvc.perform(get("/api/admin/content/library/books")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.param("size", "500"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.size").value(100));
	}

	@Test
	void adminCanCrawlTheContentLibraryAndAuditIsRecorded() throws Exception {
		String adminToken = adminLogin();
		when(contentProvider.getCatalogPage(anyInt(), any(ContentLocale.class))).thenAnswer(invocation -> {
			int page = invocation.getArgument(0);
			return new ContentProvider.ContentCatalogPage(List.of(
					new ContentBook("lib-" + page, "Library " + page, "library-" + page,
							"https://example.com/library.jpg", "", 5)),
					true);
		});

		mockMvc.perform(post("/api/admin/content/library/crawl")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.fetchedPages").value(13))
				.andExpect(jsonPath("$.data.added").value(13))
				.andExpect(jsonPath("$.data.total").value(13))
				.andExpect(jsonPath("$.data.nextPage").value(13))
				.andExpect(jsonPath("$.data.hasMore").value(true));

		mockMvc.perform(get("/api/admin/content/library")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.bookCount").value(13))
				.andExpect(jsonPath("$.data.nextPage").value(13))
				.andExpect(jsonPath("$.data.lastAdded").value(13));

		mockMvc.perform(get("/api/admin/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[*].action", hasItem("CONTENT_LIBRARY_CRAWLED")));
	}

	@Test
	void appTokenCannotAccessContentLibrary() throws Exception {
		String appToken = registerAndExtractAppToken("library-boundary-user");

		mockMvc.perform(get("/api/admin/content/library")
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
		return TestAppUsers.token(mockMvc, objectMapper, username);
	}
}
