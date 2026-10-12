package com.reelshort.backend.system.alerts;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.system.runtime.RuntimeDependencyStatus;
import com.reelshort.backend.system.runtime.SystemRuntimeResponse;

@Service
public class SystemAlertService {

	private static final Pattern DIAGNOSTICS_EVENTS = Pattern.compile("diagnostics events=(\\d+)");

	private final SystemAlertRepository systemAlertRepository;
	private final Clock clock;

	/**
	 * P3-20：内容源诊断计数器是进程内累计值（永不回落）。按“水位线”判定：后端只对上次评估之后
	 * 新增的事件告警，counter 不再增长时自动 resolve，避免内容源恢复后告警长期 OPEN。
	 *
	 * <p>水位线是**进程级**状态（与内容源进程内的累计计数器同生命周期），因此刻意声明为 static：
	 * 同一 JVM 内任何评估入口（定时任务、管理员手动触发）都共享同一基线。进程重启后水位线回到空，
	 * 首次观测只记录基线而不告警，所以重启不会产生误报。
	 */
	private static final ConcurrentHashMap<String, Integer> DIAGNOSTICS_WATERMARKS = new ConcurrentHashMap<>();

	/** 仅供测试与诊断：清空进程级水位线。 */
	public static void resetDiagnosticsWatermarks() {
		DIAGNOSTICS_WATERMARKS.clear();
	}

	public SystemAlertService(SystemAlertRepository systemAlertRepository, Clock clock) {
		this.systemAlertRepository = systemAlertRepository;
		this.clock = clock;
	}

	@Transactional
	public List<SystemAlertResponse> evaluate(SystemRuntimeResponse runtime) {
		Instant now = clock.instant();
		Set<String> observedKeys = new LinkedHashSet<>();
		for (RuntimeDependencyStatus dependency : runtime.dependencies()) {
			String alertKey = dependencyAlertKey(dependency.name());
			if ("DOWN".equals(dependency.status())) {
				observedKeys.add(alertKey);
				upsertDependencyAlert(alertKey, dependency, now);
				resolveProviderDiagnosticsAlert(dependency.name(), now);
			}
			else {
				resolveAlert(alertKey, now);
				if (hasNewContentProviderDiagnosticsEvents(dependency)) {
					String diagnosticsAlertKey = contentProviderDiagnosticsAlertKey();
					observedKeys.add(diagnosticsAlertKey);
					upsertContentProviderDiagnosticsAlert(diagnosticsAlertKey, dependency, now);
				}
				else {
					resolveProviderDiagnosticsAlert(dependency.name(), now);
				}
			}
		}
		systemAlertRepository.findAllByOrderByLastSeenAtDesc().stream()
				.filter(alert -> alert.alertKey().startsWith("runtime:dependency:"))
				.filter(alert -> !observedKeys.contains(alert.alertKey()))
				.filter(alert -> alert.status() != SystemAlertStatus.RESOLVED)
				.forEach(alert -> alert.resolve(now));
		return alerts(null);
	}

	@Transactional(readOnly = true)
	public List<SystemAlertResponse> alerts(SystemAlertStatus status) {
		List<SystemAlert> alerts = status == null
				? systemAlertRepository.findAllByOrderByLastSeenAtDesc()
				: systemAlertRepository.findByStatusOrderByLastSeenAtDesc(status);
		return alerts.stream().map(SystemAlertResponse::from).toList();
	}

	@Transactional
	public SystemAlertResponse acknowledge(UUID alertId, String adminUsername) {
		SystemAlert alert = systemAlertRepository.findById(alertId)
				.orElseThrow(() -> new SystemAlertException(HttpStatus.NOT_FOUND.value(), "alert not found"));
		alert.acknowledge(adminUsername, clock.instant());
		return SystemAlertResponse.from(systemAlertRepository.save(alert));
	}

	private void upsertDependencyAlert(String alertKey, RuntimeDependencyStatus dependency, Instant now) {
		SystemAlertSeverity severity = dependencySeverity(dependency.name());
		String title = "Runtime dependency down: " + dependency.name();
		String detail = sanitizeDetail(dependency.detail());
		SystemAlert alert = systemAlertRepository.findByAlertKey(alertKey)
				.orElseGet(() -> SystemAlert.open(alertKey, severity, title, detail, now));
		alert.observe(severity, title, detail, now);
		systemAlertRepository.save(alert);
	}

	private void upsertContentProviderDiagnosticsAlert(String alertKey, RuntimeDependencyStatus dependency, Instant now) {
		String title = "Content provider diagnostics reported events";
		String detail = sanitizeDetail(dependency.detail());
		SystemAlert alert = systemAlertRepository.findByAlertKey(alertKey)
				.orElseGet(() -> SystemAlert.open(alertKey, SystemAlertSeverity.WARNING, title, detail, now));
		alert.observe(SystemAlertSeverity.WARNING, title, detail, now);
		systemAlertRepository.save(alert);
	}

	private void resolveAlert(String alertKey, Instant now) {
		systemAlertRepository.findByAlertKey(alertKey)
				.filter(alert -> alert.status() != SystemAlertStatus.RESOLVED)
				.ifPresent(alert -> alert.resolve(now));
	}

	private String dependencyAlertKey(String dependencyName) {
		return "runtime:dependency:" + dependencyName;
	}

	private void resolveProviderDiagnosticsAlert(String dependencyName, Instant now) {
		if ("content-provider".equals(dependencyName)) {
			resolveAlert(contentProviderDiagnosticsAlertKey(), now);
		}
	}

	private String contentProviderDiagnosticsAlertKey() {
		return "runtime:dependency:content-provider:diagnostics";
	}

	/**
	 * P3-20：相对水位线判断是否出现了新的内容源诊断事件。首次观测只记录基线不告警，
	 * 之后仅在计数器增长时告警；不再增长即视为已恢复，由调用方 resolve 告警。
	 */
	private boolean hasNewContentProviderDiagnosticsEvents(RuntimeDependencyStatus dependency) {
		if (!"content-provider".equals(dependency.name())) {
			return false;
		}
		Integer totalEvents = parseDiagnosticsTotalEvents(dependency.detail());
		if (totalEvents == null) {
			return false;
		}
		Integer previous = DIAGNOSTICS_WATERMARKS.put("content-provider", totalEvents);
		return previous != null && totalEvents > previous;
	}

	/**
	 * 从运行诊断 detail 中解析累计事件数（形如 {@code reachable; diagnostics events=3, ...}）。
	 */
	private Integer parseDiagnosticsTotalEvents(String detail) {
		if (detail == null) {
			return null;
		}
		Matcher matcher = DIAGNOSTICS_EVENTS.matcher(detail);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Integer.valueOf(matcher.group(1));
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private SystemAlertSeverity dependencySeverity(String dependencyName) {
		return "database".equals(dependencyName) ? SystemAlertSeverity.CRITICAL : SystemAlertSeverity.WARNING;
	}

	private String sanitizeDetail(String detail) {
		if (detail == null || detail.isBlank()) {
			return "unavailable";
		}
		return detail.length() > 512 ? detail.substring(0, 512) : detail;
	}
}
