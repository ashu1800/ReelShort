package com.reelshort.backend.content;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reelshort.backend.system.web.GlobalExceptionHandler;
import com.reelshort.backend.system.web.RequestIdFilter;

@WebMvcTest(controllers = ContentController.class)
class ContentControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContentProvider contentProvider;

	@Test
	void searchReturnsBooksInUnifiedEnvelope() throws Exception {
		when(contentProvider.search("love")).thenReturn(List.of(
				new ContentBook("book-1", "Love Story", "love-story", "https://example.com/cover.jpg", 12)));

		mockMvc.perform(get("/api/app/content/search").param("keywords", "love"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value(0))
				.andExpect(jsonPath("$.message").value("success"))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].bookId").value("book-1"))
				.andExpect(jsonPath("$.data[0].title").value("Love Story"));
	}
}
