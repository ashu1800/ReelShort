package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.reelshort.backend.system.alerts.SystemAlert;
import com.reelshort.backend.system.alerts.SystemAlertRepository;
import com.reelshort.backend.system.alerts.SystemAlertResponse;
import com.reelshort.backend.system.alerts.SystemAlertService;
import com.reelshort.backend.system.alerts.SystemAlertSeverity;
import com.reelshort.backend.system.alerts.SystemAlertStatus;
import com.reelshort.backend.system.runtime.RuntimeDependencyStatus;
import com.reelshort.backend.system.runtime.SystemRuntimeResponse;

class SystemAlertServiceTests {

	private final Map<String, SystemAlert> alerts = new LinkedHashMap<>();
	private final SystemAlertRepository repository = repository();
	private final Clock clock = Clock.fixed(Instant.parse("2026-06-27T10:00:00Z"), ZoneOffset.UTC);
	private final SystemAlertService service = new SystemAlertService(repository, clock);

	@Test
	void downDependencyCreatesOpenAlert() {
		SystemRuntimeResponse runtime = runtime(List.of(RuntimeDependencyStatus.down("redis", "unavailable")));

		List<SystemAlertResponse> alerts = service.evaluate(runtime);

		assertThat(alerts).hasSize(1);
		SystemAlertResponse alert = alerts.get(0);
		assertThat(alert.alertKey()).isEqualTo("runtime:dependency:redis");
		assertThat(alert.status()).isEqualTo(SystemAlertStatus.OPEN.name());
		assertThat(alert.severity()).isEqualTo(SystemAlertSeverity.WARNING.name());
		assertThat(alert.title()).isEqualTo("Runtime dependency down: redis");
		assertThat(alert.detail()).isEqualTo("unavailable");
		assertThat(alert.firstSeenAt()).isEqualTo("2026-06-27T10:00:00Z");
		assertThat(alert.lastSeenAt()).isEqualTo("2026-06-27T10:00:00Z");
		assertThat(this.alerts).hasSize(1);
	}

	@Test
	void repeatedDownDependencyUpdatesExistingAlert() {
		service.evaluate(runtime(List.of(RuntimeDependencyStatus.down("redis", "unavailable"))));
		SystemAlertService laterService = new SystemAlertService(repository,
				Clock.fixed(Instant.parse("2026-06-27T10:05:00Z"), ZoneOffset.UTC));

		List<SystemAlertResponse> alerts = laterService.evaluate(
				runtime(List.of(RuntimeDependencyStatus.down("redis", "timeout"))));

		assertThat(this.alerts).hasSize(1);
		SystemAlertResponse alert = alerts.get(0);
		assertThat(alert.firstSeenAt()).isEqualTo("2026-06-27T10:00:00Z");
		assertThat(alert.lastSeenAt()).isEqualTo("2026-06-27T10:05:00Z");
		assertThat(alert.detail()).isEqualTo("timeout");
		assertThat(alert.status()).isEqualTo(SystemAlertStatus.OPEN.name());
	}

	@Test
	void recoveredDependencyResolvesOpenAlert() {
		service.evaluate(runtime(List.of(RuntimeDependencyStatus.down("database", "unavailable"))));
		SystemAlertService laterService = new SystemAlertService(repository,
				Clock.fixed(Instant.parse("2026-06-27T10:10:00Z"), ZoneOffset.UTC));

		List<SystemAlertResponse> alerts = laterService.evaluate(
				runtime(List.of(RuntimeDependencyStatus.up("database", "validated"))));

		SystemAlertResponse alert = alerts.get(0);
		assertThat(alert.alertKey()).isEqualTo("runtime:dependency:database");
		assertThat(alert.severity()).isEqualTo(SystemAlertSeverity.CRITICAL.name());
		assertThat(alert.status()).isEqualTo(SystemAlertStatus.RESOLVED.name());
		assertThat(alert.resolvedAt()).isEqualTo("2026-06-27T10:10:00Z");
	}

	private SystemRuntimeResponse runtime(List<RuntimeDependencyStatus> dependencies) {
		String status = dependencies.stream().allMatch(dependency -> "UP".equals(dependency.status()))
				? "UP"
				: "DEGRADED";
		return new SystemRuntimeResponse(status, clock.instant().toString(),
				new SystemRuntimeResponse.ApplicationInfo("reelshort-backend", "test", "17", 10),
				new SystemRuntimeResponse.MemoryInfo(1, 100),
				dependencies);
	}

	private SystemAlertRepository repository() {
		SystemAlertRepository repository = Mockito.mock(SystemAlertRepository.class);
		Mockito.when(repository.findByAlertKey(Mockito.anyString()))
				.thenAnswer(invocation -> Optional.ofNullable(alerts.get(invocation.getArgument(0))));
		Mockito.when(repository.findAllByOrderByLastSeenAtDesc())
				.thenAnswer(invocation -> List.copyOf(alerts.values()));
		Mockito.when(repository.findByStatusOrderByLastSeenAtDesc(Mockito.any(SystemAlertStatus.class)))
				.thenAnswer(invocation -> {
					SystemAlertStatus status = invocation.getArgument(0);
					return alerts.values().stream()
							.filter(alert -> alert.status() == status)
							.toList();
				});
		Mockito.when(repository.save(Mockito.any(SystemAlert.class)))
				.thenAnswer(invocation -> {
					SystemAlert alert = invocation.getArgument(0);
					alerts.put(alert.alertKey(), alert);
					return alert;
				});
		return repository;
	}
}
