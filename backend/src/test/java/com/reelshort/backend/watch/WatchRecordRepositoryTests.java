package com.reelshort.backend.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class WatchRecordRepositoryTests {

	@Autowired
	private WatchRecordRepository watchRecordRepository;

	@Test
	void userBookEpisodeCombinationIsUnique() {
		UUID userId = UUID.randomUUID();
		WatchRecord first = WatchRecord.create(userId, "book-unique", "Title", "filtered", 1, "chapter-1");
		first.update("Title", "filtered", "chapter-1", 10, 100, 10);
		watchRecordRepository.saveAndFlush(first);

		WatchRecord duplicate = WatchRecord.create(userId, "book-unique", "Title", "filtered", 1, "chapter-1");
		duplicate.update("Title", "filtered", "chapter-1", 20, 100, 20);

		assertThatThrownBy(() -> watchRecordRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findsHistoryByNewestFirst() {
		UUID userId = UUID.randomUUID();
		WatchRecord older = WatchRecord.create(userId, "book-old", "Old", "old", 1, "chapter-1");
		older.update("Old", "old", "chapter-1", 10, 100, 10);
		watchRecordRepository.saveAndFlush(older);

		WatchRecord newer = WatchRecord.create(userId, "book-new", "New", "new", 1, "chapter-1");
		newer.update("New", "new", "chapter-1", 20, 100, 20);
		watchRecordRepository.saveAndFlush(newer);

		assertThat(watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId))
				.extracting(WatchRecord::bookId)
				.containsExactly("book-new", "book-old");
	}
}
