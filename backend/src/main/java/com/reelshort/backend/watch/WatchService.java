package com.reelshort.backend.watch;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchService {

	private final WatchRecordRepository watchRecordRepository;

	public WatchService(WatchRecordRepository watchRecordRepository) {
		this.watchRecordRepository = watchRecordRepository;
	}

	@Transactional
	public WatchRecordResponse reportProgress(UUID userId, WatchProgressRequest request) {
		WatchRecord record = watchRecordRepository
				.findByUserIdAndBookIdAndEpisodeNum(userId, request.bookId(), request.episodeNum())
				.orElseGet(() -> WatchRecord.create(userId, request.bookId(), request.bookTitle(),
						request.filteredTitle(), request.episodeNum(), request.chapterId()));
		int clampedPosition = Math.min(request.positionSeconds(), request.durationSeconds());
		record.update(request.bookTitle(), request.filteredTitle(), request.chapterId(), clampedPosition,
				request.durationSeconds(), progressPercent(clampedPosition, request.durationSeconds()));
		return WatchRecordResponse.from(watchRecordRepository.save(record));
	}

	@Transactional(readOnly = true)
	public List<WatchRecordResponse> history(UUID userId) {
		return watchRecordRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(WatchRecordResponse::from)
				.toList();
	}

	private int progressPercent(int positionSeconds, int durationSeconds) {
		return Math.min(100, (int) Math.floor(positionSeconds * 100.0 / durationSeconds));
	}
}
