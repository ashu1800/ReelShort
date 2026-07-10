package com.reelshort.backend.operations;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InternalWatchProgressRequest(
		@NotBlank String bookId,
		@NotBlank String bookTitle,
		@NotBlank String filteredTitle,
		@Min(1) int episodeNum,
		@NotBlank String chapterId,
		@Min(0) int positionSeconds,
		@Min(1) int durationSeconds,
		@Min(1) @Max(100) int progressPercent,
		@NotBlank @Size(max = 160) String reason) {
}
