package com.reelshort.backend.watch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 观看进度上报请求。字段长度与 {@code watch_records} 列宽对齐（P3-13），
 * 超长输入在参数校验阶段返回 400，而不是落库失败后变成 500。
 */
public record WatchProgressRequest(
		@NotBlank @Size(max = 128) String bookId,
		@NotBlank @Size(max = 255) String bookTitle,
		@NotBlank @Size(max = 255) String filteredTitle,
		@Min(1) int episodeNum,
		@NotBlank @Size(max = 128) String chapterId,
		@Min(0) int positionSeconds,
		@Min(1) int durationSeconds) {
}
