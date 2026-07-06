package com.reelshort.backend.system.alerts;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reelshort.backend.system.runtime.RuntimeDependencyStatus;
import com.reelshort.backend.system.runtime.SystemRuntimeResponse;

@Service
public class SystemAlertService {

	private final SystemAlertRepository systemAlertRepository;
	private final Clock clock;

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
				if (hasContentProviderDiagnosticsEvents(dependency)) {
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

	private boolean hasContentProviderDiagnosticsEvents(RuntimeDependencyStatus dependency) {
		if (!"content-provider".equals(dependency.name())) {
			return false;
		}
		String detail = dependency.detail();
		return detail != null && detail.contains("diagnostics events=");
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
