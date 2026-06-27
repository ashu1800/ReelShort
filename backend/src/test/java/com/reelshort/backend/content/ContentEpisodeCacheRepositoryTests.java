package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;

@DataJpaTest
class ContentEpisodeCacheRepositoryTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private ContentEpisodeCacheRepository contentEpisodeCacheRepository;

	@Test
	void findsCacheByBookIdAndFilteredTitle() {
		ContentEpisodeCache cache = contentEpisodeCacheRepository.saveAndFlush(cache("book-1", "love-story",
				List.of(new ContentEpisode(1, "chapter-1"))));

		assertThat(contentEpisodeCacheRepository.findByBookIdAndFilteredTitle("book-1", "love-story"))
				.contains(cache);
	}

	@Test
	void bookIdAndFilteredTitleAreUnique() {
		contentEpisodeCacheRepository.saveAndFlush(cache("book-1", "love-story", List.of()));

		assertThatThrownBy(() -> contentEpisodeCacheRepository.saveAndFlush(
				cache("book-1", "love-story", List.of(new ContentEpisode(1, "chapter-1")))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private ContentEpisodeCache cache(String bookId, String filteredTitle, List<ContentEpisode> episodes) {
		try {
			return ContentEpisodeCache.create(bookId, filteredTitle, objectMapper.writeValueAsString(episodes),
					episodes.size());
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
