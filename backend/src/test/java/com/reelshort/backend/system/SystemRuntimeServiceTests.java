package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.reelshort.backend.system.runtime.RuntimeDependencyChecker;
import com.reelshort.backend.system.runtime.RuntimeDependencyStatus;
import com.reelshort.backend.system.runtime.SystemRuntimeResponse;
import com.reelshort.backend.system.runtime.SystemRuntimeService;

class SystemRuntimeServiceTests {

	private final Clock clock = Clock.fixed(Instant.parse("2026-06-27T09:30:00Z"), ZoneOffset.UTC);

	@Test
	void allDependenciesUpProducesUpSummary() {
		SystemRuntimeService service = new SystemRuntimeService(List.of(
				() -> RuntimeDependencyStatus.up("database", "validated"),
				() -> RuntimeDependencyStatus.up("redis", "pong")), clock, "0.0.1-test");

		SystemRuntimeResponse response = service.snapshot();

		assertThat(response.status()).isEqualTo("UP");
		assertThat(response.checkedAt()).isEqualTo("2026-06-27T09:30:00Z");
		assertThat(response.application().service()).isEqualTo("reelshort-backend");
		assertThat(response.application().version()).isEqualTo("0.0.1-test");
		assertThat(response.application().javaVersion()).isNotBlank();
		assertThat(response.application().uptimeSeconds()).isGreaterThanOrEqualTo(0);
		assertThat(response.memory().usedBytes()).isGreaterThanOrEqualTo(0);
		assertThat(response.memory().maxBytes()).isGreaterThan(0);
		assertThat(response.dependencies()).extracting(RuntimeDependencyStatus::name)
				.containsExactly("database", "redis");
	}

	@Test
	void failingDependencyProducesDegradedSummary() {
		SystemRuntimeService service = new SystemRuntimeService(List.of(
				() -> RuntimeDependencyStatus.up("database", "validated"),
				() -> RuntimeDependencyStatus.down("redis", "unavailable")), clock, "0.0.1-test");

		SystemRuntimeResponse response = service.snapshot();

		assertThat(response.status()).isEqualTo("DEGRADED");
		assertThat(response.dependencies()).extracting(RuntimeDependencyStatus::status)
				.containsExactly("UP", "DOWN");
	}

	@Test
	void thrownDependencyExceptionIsSanitized() {
		RuntimeDependencyChecker checker = () -> {
			throw new IllegalStateException("password=secret stack trace detail");
		};
		SystemRuntimeService service = new SystemRuntimeService(List.of(checker), clock, "0.0.1-test");

		SystemRuntimeResponse response = service.snapshot();

		assertThat(response.status()).isEqualTo("DEGRADED");
		assertThat(response.dependencies()).hasSize(1);
		RuntimeDependencyStatus dependency = response.dependencies().get(0);
		assertThat(dependency.name()).isEqualTo("unknown");
		assertThat(dependency.status()).isEqualTo("DOWN");
		assertThat(dependency.detail()).isEqualTo("check failed");
		assertThat(dependency.detail()).doesNotContain("secret", "stack trace");
	}
}
