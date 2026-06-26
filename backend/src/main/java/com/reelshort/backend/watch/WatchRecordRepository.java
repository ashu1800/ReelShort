package com.reelshort.backend.watch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchRecordRepository extends JpaRepository<WatchRecord, UUID> {

	Optional<WatchRecord> findByUserIdAndBookIdAndEpisodeNum(UUID userId, String bookId, int episodeNum);

	List<WatchRecord> findByUserIdOrderByUpdatedAtDesc(UUID userId);

	long countByUserId(UUID userId);
}
