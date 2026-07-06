package com.reelshort.backend.system.runtime;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SystemRuntimeResponse(
		String status,
		String checkedAt,
		ApplicationInfo application,
		MemoryInfo memory,
		List<RuntimeDependencyStatus> dependencies,
		@JsonInclude(JsonInclude.Include.ALWAYS)
		ContentProviderDiagnostics contentProviderDiagnostics) {

	public SystemRuntimeResponse(String status, String checkedAt, ApplicationInfo application, MemoryInfo memory,
			List<RuntimeDependencyStatus> dependencies) {
		this(status, checkedAt, application, memory, dependencies, null);
	}

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

	public record ContentProviderDiagnostics(
			int totalEvents,
			Map<String, Integer> counters,
			List<ContentProviderDiagnosticEvent> recentEvents) {
	}

	public record ContentProviderDiagnosticEvent(
			String eventType,
			String observedAt,
			Map<String, String> context) {
	}
}
