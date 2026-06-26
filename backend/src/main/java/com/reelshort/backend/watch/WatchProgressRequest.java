package com.reelshort.backend.watch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record WatchProgressRequest(
		@NotBlank String bookId,
		@NotBlank String bookTitle,
		@NotBlank String filteredTitle,
		@Min(1) int episodeNum,
		@NotBlank String chapterId,
		@Min(0) int positionSeconds,
		@Min(1) int durationSeconds) {
}
