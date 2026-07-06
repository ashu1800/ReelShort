package com.reelshort.backend.system.runtime;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SystemRuntimeService {

	private static final String SERVICE_NAME = "reelshort-backend";

	private final List<RuntimeDependencyChecker> dependencyCheckers;
	private final Clock clock;
	private final String version;

	public SystemRuntimeService(List<RuntimeDependencyChecker> dependencyCheckers, Clock clock,
			@Value("${spring.application.version:${info.app.version:0.0.1-SNAPSHOT}}") String version) {
		this.dependencyCheckers = List.copyOf(dependencyCheckers);
		this.clock = clock;
		this.version = version;
	}

	public SystemRuntimeResponse snapshot() {
		List<RuntimeDependencyStatus> dependencies = dependencyCheckers.stream()
				.map(this::safeCheck)
				.toList();
		String status = dependencies.stream().allMatch(dependency -> "UP".equals(dependency.status()))
				? "UP"
				: "DEGRADED";

		Runtime runtime = Runtime.getRuntime();
		long usedBytes = runtime.totalMemory() - runtime.freeMemory();
		long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

		return new SystemRuntimeResponse(
				status,
				clock.instant().toString(),
				new SystemRuntimeResponse.ApplicationInfo(
						SERVICE_NAME,
						version,
						System.getProperty("java.version", "unknown"),
						uptimeSeconds),
				new SystemRuntimeResponse.MemoryInfo(usedBytes, runtime.maxMemory()),
				dependencies,
				contentProviderDiagnostics());
	}

	private RuntimeDependencyStatus safeCheck(RuntimeDependencyChecker checker) {
		try {
			RuntimeDependencyStatus status = checker.check();
			return status == null ? RuntimeDependencyStatus.down("unknown", "check returned empty") : status;
		}
		catch (RuntimeException exception) {
			return RuntimeDependencyStatus.down("unknown", "check failed");
		}
	}

	private SystemRuntimeResponse.ContentProviderDiagnostics contentProviderDiagnostics() {
		return dependencyCheckers.stream()
				.filter(ContentProviderRuntimeDependencyChecker.class::isInstance)
				.map(ContentProviderRuntimeDependencyChecker.class::cast)
				.map(ContentProviderRuntimeDependencyChecker::latestDiagnostics)
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}
}
