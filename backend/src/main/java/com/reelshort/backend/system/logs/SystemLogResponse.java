package com.reelshort.backend.system.logs;

import java.util.List;

public record SystemLogResponse(
		List<String> files,
		String selectedFile,
		int requestedLines,
		int lineCount,
		boolean truncated,
		String updatedAt,
		List<String> lines) {
}
