package com.reelshort.backend.social;

import static org.hamcrest.Matchers.hasSize;
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
				.andExpect(jsonPath("$.data.count").value(1));

		mockMvc.perform(get("/api/app/social/books/book-like/like-status")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.data.active").value(true));

		mockMvc.perform(post("/api/app/social/books/book-like/like")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(jsonPath("$.data.active").value(false))
				.andExpect(jsonPath("$.data.count").value(0));
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
		String token = registerAndExtractToken("social-carol");

		mockMvc.perform(post("/api/app/social/books/book-comment/comments")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(commentBody("nice drama")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.data.username").value("social-carol"))
				.andExpect(jsonPath("$.data.content").value("nice drama"));

		// 评论列表对游客开放阅读
		mockMvc.perform(get("/api/app/social/books/book-comment/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].content").value("nice drama"));
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
