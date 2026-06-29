package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;

@DataJpaTest
class ContentVideoCacheRepositoryTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private ContentVideoCacheRepository contentVideoCacheRepository;

	@Test
	void findsCacheByCompositeKey() {
		ContentVideoCache cache = contentVideoCacheRepository.saveAndFlush(cache("book-1", 1, "love-story", "chapter-1"));

		assertThat(
				contentVideoCacheRepository.findByBookIdAndEpisodeNumAndFilteredTitleAndChapterId("book-1", 1,
						"love-story", "chapter-1"))
				.contains(cache);
	}

	@Test
	void compositeKeyIsUnique() {
		contentVideoCacheRepository.saveAndFlush(cache("book-1", 1, "love-story", "chapter-1"));

		assertThatThrownBy(() -> contentVideoCacheRepository
				.saveAndFlush(cache("book-1", 1, "love-story", "chapter-1")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void differentEpisodeOrChapterIsAllowed() {
		contentVideoCacheRepository.saveAndFlush(cache("book-1", 1, "love-story", "chapter-1"));
		contentVideoCacheRepository.saveAndFlush(cache("book-1", 2, "love-story", "chapter-2"));

		assertThat(contentVideoCacheRepository.count()).isEqualTo(2);
	}

	private ContentVideoCache cache(String bookId, int episodeNum, String filteredTitle, String chapterId) {
		try {
			return ContentVideoCache.create(bookId, episodeNum, filteredTitle, chapterId,
					objectMapper.writeValueAsString(new ContentVideo("https://cdn.example.com/" + episodeNum + ".m3u8",
							episodeNum, 120, null)));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}
}
