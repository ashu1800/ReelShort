package com.reelshort.backend.system.runtime;

import java.util.List;

public record SystemRuntimeResponse(
		String status,
		String checkedAt,
		ApplicationInfo application,
		MemoryInfo memory,
		List<RuntimeDependencyStatus> dependencies) {

	public record ApplicationInfo(
			String service,
			String version,
			String javaVersion,
			long uptimeSeconds) {
	}

	public record MemoryInfo(
			long usedBytes,
			long maxBytes) {
	}
}
