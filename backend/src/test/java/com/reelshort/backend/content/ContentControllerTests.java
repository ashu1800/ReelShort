package com.reelshort.backend.content;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reelshort.backend.auth.AccessTokenRepository;
import com.reelshort.backend.auth.TokenHasher;
import com.reelshort.backend.admin.AdminTokenRepository;
import com.reelshort.backend.admin.AdminUserRepository;
import com.reelshort.backend.system.web.GlobalExceptionHandler;
import com.reelshort.backend.system.web.RequestIdFilter;

@WebMvcTest(controllers = {ContentController.class, HomeController.class})
@AutoConfigureMockMvc(addFilters = false)
class ContentControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContentCacheService contentCacheService;

	@MockitoBean
	private AccessTokenRepository accessTokenRepository;

	@MockitoBean
	private AdminTokenRepository adminTokenRepository;

	@MockitoBean
	private AdminUserRepository adminUserRepository;

	@MockitoBean
	private TokenHasher tokenHasher;

	@Test
	void searchReturnsBooksInUnifiedEnvelope() throws Exception {
		when(contentCacheService.search("love")).thenReturn(List.of(
				new ContentBook("book-1", "Love Story", "love-story", "https://example.com/cover.jpg", 12)));

		mockMvc.perform(get("/api/app/content/search").param("keywords", "love"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.message").value("success"))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("book-1"))
				.andExpect(jsonPath("$.data[0].title").value("Love Story"));
	}

	@Test
	void searchRequiresKeywords() throws Exception {
		mockMvc.perform(get("/api/app/content/search"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.path").value("/api/app/content/search"));
	}

	@Test
	void searchRejectsBlankKeywords() throws Exception {
		mockMvc.perform(get("/api/app/content/search").param("keywords", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400))
				.andExpect(jsonPath("$.path").value("/api/app/content/search"));
	}

	@Test
	void contentProviderFailureReturnsServiceUnavailable() throws Exception {
		when(contentCacheService.search(anyString()))
				.thenThrow(new ContentProviderException(503, "content provider unavailable"));

		mockMvc.perform(get("/api/app/content/search").param("keywords", "love"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value(503))
				.andExpect(jsonPath("$.message").value("content provider unavailable"))
				.andExpect(jsonPath("$.path").value("/api/app/content/search"));
	}

	@Test
	void episodesReturnsProviderEpisodesInUnifiedEnvelope() throws Exception {
		when(contentCacheService.getEpisodes("book-1", "love-story")).thenReturn(List.of(
				new ContentEpisode(1, "chapter-1"),
				new ContentEpisode(2, "chapter-2")));

		mockMvc.perform(get("/api/app/content/books/book-1/episodes")
				.param("filteredTitle", "love-story"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].episode").value(1))
				.andExpect(jsonPath("$.data[0].chapterId").value("chapter-1"));
	}

	@Test
	void detailReturnsCachedBookInUnifiedEnvelope() throws Exception {
		when(contentCacheService.getBook("book-1"))
				.thenReturn(new ContentBook("book-1", "Love Story", "love-story",
						"https://example.com/cover.jpg", 12));

		mockMvc.perform(get("/api/app/content/books/book-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.bookId").value("book-1"))
				.andExpect(jsonPath("$.data.title").value("Love Story"))
				.andExpect(jsonPath("$.data.filteredTitle").value("love-story"))
				.andExpect(jsonPath("$.data.chapterCount").value(12));
	}

	@Test
	void playReturnsProviderVideoInUnifiedEnvelope() throws Exception {
		when(contentCacheService.getVideoUrl("book-1", 1, "love-story", "chapter-1"))
				.thenReturn(new ContentVideo(
						"https://cdn.example.com/video.m3u8",
						1,
						120,
						new ContentEpisode(2, "chapter-2")));

		mockMvc.perform(get("/api/app/content/books/book-1/episodes/1/play")
				.param("filteredTitle", "love-story")
				.param("chapterId", "chapter-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.videoUrl").value("https://cdn.example.com/video.m3u8"))
				.andExpect(jsonPath("$.data.episode").value(1))
				.andExpect(jsonPath("$.data.duration").value(120))
				.andExpect(jsonPath("$.data.nextEpisode.episode").value(2))
				.andExpect(jsonPath("$.data.nextEpisode.chapterId").value("chapter-2"));
	}

	@Test
	void homeRecommendReturnsRecommendShelf() throws Exception {
		when(contentCacheService.getShelf(ContentShelfType.RECOMMEND)).thenReturn(List.of(
				new ContentBook("book-home", "Home Pick", "home-pick", "https://example.com/home.jpg", 6)));

		mockMvc.perform(get("/api/app/home/recommend"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("book-home"));
	}

	@Test
	void shelfEndpointReturnsSelectedShelf() throws Exception {
		when(contentCacheService.getShelf(ContentShelfType.NEW_RELEASE)).thenReturn(List.of(
				new ContentBook("book-new", "New", "new", "https://example.com/new.jpg", 2)));

		mockMvc.perform(get("/api/app/content/shelves/new-release"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("book-new"));
	}

	@Test
	void shelfEndpointRejectsUnknownShelf() throws Exception {
		mockMvc.perform(get("/api/app/content/shelves/unknown"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}
}
