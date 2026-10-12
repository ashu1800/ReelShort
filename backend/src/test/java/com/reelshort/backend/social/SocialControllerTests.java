package com.reelshort.backend.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
class SocialControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void likeRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/app/social/books/book-1/like")
				.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(401));
	}

	@Test
	void toggleLikeFlipsState() throws Exception {
		String token = registerAndExtractToken("social-alice");

		mockMvc.perform(post("/api/app/social/books/book-like/like")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.active").value(true))
				.andExpect(jsonPath("$.data.count", greaterThan(1)));

		mockMvc.perform(get("/api/app/social/books/book-like/like-status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.data.active").value(true));

		mockMvc.perform(post("/api/app/social/books/book-like/like")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.data.active").value(false))
				.andExpect(jsonPath("$.data.count", greaterThan(0)));
	}

	@Test
	void toggleFavoriteStoresSnapshot() throws Exception {
		String token = registerAndExtractToken("social-bob");

		mockMvc.perform(post("/api/app/social/books/book-fav/favorite")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(favoriteBody("Love Story", "love-story", "http://cover", 12)))
				.andExpect(jsonPath("$.data.active").value(true));

		mockMvc.perform(get("/api/app/social/my/favorites")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("book-fav"))
				.andExpect(jsonPath("$.data[0].chapterCount").value(12));
	}

	@Test
	void commentsCanBeAddedAndListed() throws Exception {
		TestAppUsers.RegisteredUser user = TestAppUsers.register(mockMvc, objectMapper, "social-carol");

		mockMvc.perform(post("/api/app/social/books/book-comment/comments")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(commentBody("nice drama")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.username").value(user.username()))
				.andExpect(jsonPath("$.data.content").value("nice drama"));

		// 评论列表对游客开放阅读
		mockMvc.perform(get("/api/app/social/books/book-comment/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.comments.length()", greaterThan(1)))
				.andExpect(jsonPath("$.data.comments[0].content").value("nice drama"))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(20));
	}

	@Test
	void commentsPageSizeIsCapped() throws Exception {
		mockMvc.perform(get("/api/app/social/books/book-page-cap/comments").param("size", "500"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void commentsRejectNegativePage() throws Exception {
		mockMvc.perform(get("/api/app/social/books/book-page-negative/comments").param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	@Test
	void commentsOutOfRangePageReturnsEmptyPage() throws Exception {
		mockMvc.perform(get("/api/app/social/books/book-page-out-of-range/comments").param("page", "5000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.comments.length()").value(0))
				.andExpect(jsonPath("$.data.hasMore").value(false));
	}

	@Test
	void commentsRespectRequestedPageSize() throws Exception {
		mockMvc.perform(get("/api/app/social/books/book-page-size/comments").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.comments.length()", greaterThan(0)))
				.andExpect(jsonPath("$.data.comments.length()", lessThanOrEqualTo(5)))
				.andExpect(jsonPath("$.data.size").value(5));
	}

	@Test
	void emptyCommentsReturnDisplayFallbacks() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/app/social/books/book-empty-comments/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.comments.length()", greaterThan(0)))
				.andExpect(jsonPath("$.data.comments[0].content").isNotEmpty())
				.andReturn();

		JsonNode comments = objectMapper.readTree(result.getResponse().getContentAsString())
				.get("data")
				.get("comments");
		for (JsonNode comment : comments) {
			assertThat(comment.get("username").asText()).doesNotContainPattern("\\d");
		}
	}

	@Test
	void commentRejectsBlankContent() throws Exception {
		String token = registerAndExtractToken("social-erin");

		mockMvc.perform(post("/api/app/social/books/book-x/comments")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(commentBody("   ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(400));
	}

	private String registerAndExtractToken(String username) throws Exception {
		return TestAppUsers.token(mockMvc, objectMapper, username);
	}

	private String favoriteBody(String bookTitle, String filteredTitle, String coverUrl, int chapterCount) {
		return """
				{
				  "bookTitle": "%s",
				  "filteredTitle": "%s",
				  "coverUrl": "%s",
				  "chapterCount": %d
				}
				""".formatted(bookTitle, filteredTitle, coverUrl, chapterCount);
	}

	private String commentBody(String content) {
		return """
				{
				  "content": "%s"
				}
				""".formatted(content);
	}
}
