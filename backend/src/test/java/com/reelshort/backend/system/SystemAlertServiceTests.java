package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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
	private SystemAlertService service;

	@BeforeEach
	void resetState() {
		// 每个用例都从空告警表和新的水位线基线开始，避免用例之间互相污染。
		alerts.clear();
		SystemAlertService.resetDiagnosticsWatermarks();
		service = new SystemAlertService(repository, clock);
	}

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

	@Test
	void firstDiagnosticsObservationOnlySetsWatermark() {
		// P3-20：改为水位线判定后，首次观测只记录基线，不因历史累计事件误告警。
		SystemRuntimeResponse runtime = runtime(List.of(RuntimeDependencyStatus.up(
				"content-provider",
				"reachable; diagnostics events=3, next_data_404=2, video_url_missing=1")));

		List<SystemAlertResponse> alerts = service.evaluate(runtime);

		assertThat(alerts).isEmpty();
	}

	@Test
	void newContentProviderDiagnosticsEventsCreateWarningAlert() {
		service.evaluate(runtime(List.of(RuntimeDependencyStatus.up(
				"content-provider", "reachable; diagnostics events=3, next_data_404=3"))));
		SystemAlertService laterService = new SystemAlertService(repository,
				Clock.fixed(Instant.parse("2026-06-27T10:30:00Z"), ZoneOffset.UTC));

		List<SystemAlertResponse> alerts = laterService.evaluate(runtime(List.of(RuntimeDependencyStatus.up(
				"content-provider", "reachable; diagnostics events=5, next_data_404=4, search_empty=1"))));

		assertThat(alerts).hasSize(1);
		SystemAlertResponse alert = alerts.get(0);
		assertThat(alert.alertKey()).isEqualTo("runtime:dependency:content-provider:diagnostics");
		assertThat(alert.status()).isEqualTo(SystemAlertStatus.OPEN.name());
		assertThat(alert.severity()).isEqualTo(SystemAlertSeverity.WARNING.name());
		assertThat(alert.title()).isEqualTo("Content provider diagnostics reported events");
		assertThat(alert.detail()).contains("diagnostics events=5");
	}

	@Test
	void unchangedDiagnosticsCounterResolvesWarningAlert() {
		// 计数器不再增长（内容源已恢复但累计值保持）时应自动 resolve，而不是长期粘滞 OPEN。
		service.evaluate(runtime(List.of(RuntimeDependencyStatus.up(
				"content-provider", "reachable; diagnostics events=2, search_empty=2"))));
		SystemAlertService alertingService = new SystemAlertService(repository,
				Clock.fixed(Instant.parse("2026-06-27T10:10:00Z"), ZoneOffset.UTC));
		alertingService.evaluate(runtime(List.of(RuntimeDependencyStatus.up(
				"content-provider", "reachable; diagnostics events=4, search_empty=4"))));
		SystemAlertService laterService = new SystemAlertService(repository,
				Clock.fixed(Instant.parse("2026-06-27T10:15:00Z"), ZoneOffset.UTC));

		List<SystemAlertResponse> alerts = laterService.evaluate(runtime(List.of(RuntimeDependencyStatus.up(
				"content-provider", "reachable; diagnostics events=4, search_empty=4"))));

		SystemAlertResponse alert = alerts.get(0);
		assertThat(alert.alertKey()).isEqualTo("runtime:dependency:content-provider:diagnostics");
		assertThat(alert.status()).isEqualTo(SystemAlertStatus.RESOLVED.name());
		assertThat(alert.resolvedAt()).isEqualTo("2026-06-27T10:15:00Z");
	}

	@Test
	void cleanContentProviderDiagnosticsResolvesWarningAlert() {
		service.evaluate(runtime(List.of(RuntimeDependencyStatus.up(
				"content-provider", "reachable; diagnostics events=2, search_empty=2"))));
		SystemAlertService alertingService = new SystemAlertService(repository,
				Clock.fixed(Instant.parse("2026-06-27T10:12:00Z"), ZoneOffset.UTC));
		alertingService.evaluate(runtime(List.of(RuntimeDependencyStatus.up(
				"content-provider", "reachable; diagnostics events=4, search_empty=4"))));
		SystemAlertService laterService = new SystemAlertService(repository,
				Clock.fixed(Instant.parse("2026-06-27T10:15:00Z"), ZoneOffset.UTC));

		List<SystemAlertResponse> alerts = laterService.evaluate(
				runtime(List.of(RuntimeDependencyStatus.up("content-provider", "reachable; diagnostics clean"))));

		SystemAlertResponse alert = alerts.get(0);
		assertThat(alert.alertKey()).isEqualTo("runtime:dependency:content-provider:diagnostics");
		assertThat(alert.status()).isEqualTo(SystemAlertStatus.RESOLVED.name());
		assertThat(alert.resolvedAt()).isEqualTo("2026-06-27T10:15:00Z");
	}

	@Test
	void scheduledEvaluationIsConfigured() throws Exception {
		// P3-20：evaluate 挂到定时任务，而不是只靠管理员手动触发。
		java.lang.reflect.Method method = Class
				.forName("com.reelshort.backend.system.alerts.SystemAlertEvaluationService")
				.getDeclaredMethod("evaluateScheduled");

		assertThat(method.isAnnotationPresent(org.springframework.scheduling.annotation.Scheduled.class)).isTrue();
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
