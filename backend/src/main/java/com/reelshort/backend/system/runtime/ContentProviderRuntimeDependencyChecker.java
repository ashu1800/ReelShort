package com.reelshort.backend.system.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ContentProviderRuntimeDependencyChecker implements RuntimeDependencyChecker {

	private final RestClient restClient;
	private final String baseUrl;
	private volatile SystemRuntimeResponse.ContentProviderDiagnostics latestDiagnostics;

	public ContentProviderRuntimeDependencyChecker(RestClient.Builder restClientBuilder,
			@Value("${reelshort.content-provider.base-url:http://127.0.0.1:5000}") String baseUrl) {
		this.restClient = restClientBuilder.build();
		this.baseUrl = trimTrailingSlash(baseUrl);
	}

	@Override
	public RuntimeDependencyStatus check() {
		try {
			Map<?, ?> response = restClient.get()
					.uri(baseUrl + "/health")
					.retrieve()
					.body(Map.class);
			Object status = response == null ? null : response.get("status");
			if ("ok".equalsIgnoreCase(String.valueOf(status)) || "up".equalsIgnoreCase(String.valueOf(status))) {
				return RuntimeDependencyStatus.up("content-provider", diagnosticsDetail());
			}
			latestDiagnostics = null;
			return RuntimeDependencyStatus.down("content-provider", "unexpected health response");
		}
		catch (Exception exception) {
			latestDiagnostics = null;
			return RuntimeDependencyStatus.down("content-provider", "unavailable");
		}
	}

	public SystemRuntimeResponse.ContentProviderDiagnostics latestDiagnostics() {
		return latestDiagnostics;
	}

	private String diagnosticsDetail() {
		try {
			Map<?, ?> response = restClient.get()
					.uri(baseUrl + "/diagnostics")
					.retrieve()
					.body(Map.class);
			Object diagnostics = response == null ? null : response.get("diagnostics");
			if (!(diagnostics instanceof Map<?, ?> diagnosticsMap)) {
				latestDiagnostics = null;
				return "reachable; diagnostics unavailable";
			}
			latestDiagnostics = mapDiagnostics(diagnosticsMap);
			int totalEvents = asInt(diagnosticsMap.get("total_events"));
			if (totalEvents <= 0) {
				return "reachable; diagnostics clean";
			}
			Object counters = diagnosticsMap.get("counters");
			if (!(counters instanceof Map<?, ?> countersMap) || countersMap.isEmpty()) {
				return "reachable; diagnostics events=" + totalEvents;
			}
			String summary = countersMap.entrySet()
					.stream()
					.sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
					.map(entry -> entry.getKey() + "=" + asInt(entry.getValue()))
					.collect(Collectors.joining(", "));
			return "reachable; diagnostics events=" + totalEvents + ", " + summary;
		}
		catch (RuntimeException exception) {
			latestDiagnostics = null;
			return "reachable; diagnostics unavailable";
		}
	}

	private SystemRuntimeResponse.ContentProviderDiagnostics mapDiagnostics(Map<?, ?> diagnosticsMap) {
		Object counters = diagnosticsMap.get("counters");
		Map<String, Integer> counterValues = counters instanceof Map<?, ?> countersMap
				? countersMap.entrySet()
						.stream()
						.sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
						.collect(Collectors.toMap(
								entry -> String.valueOf(entry.getKey()),
								entry -> asInt(entry.getValue()),
								(left, right) -> left,
								LinkedHashMap::new))
				: Map.of();
		return new SystemRuntimeResponse.ContentProviderDiagnostics(
				asInt(diagnosticsMap.get("total_events")),
				counterValues,
				mapRecentEvents(diagnosticsMap.get("recent_events")));
	}

	private List<SystemRuntimeResponse.ContentProviderDiagnosticEvent> mapRecentEvents(Object value) {
		if (!(value instanceof List<?> events)) {
			return List.of();
		}
		return events.stream()
				.filter(Map.class::isInstance)
				.map(Map.class::cast)
				.map(this::mapRecentEvent)
				.filter(Objects::nonNull)
				.toList();
	}

	private SystemRuntimeResponse.ContentProviderDiagnosticEvent mapRecentEvent(Map<?, ?> event) {
		String eventType = stringValue(event.get("event_type"));
		String observedAt = stringValue(event.get("observed_at"));
		if (eventType.isBlank() || observedAt.isBlank()) {
			return null;
		}
		return new SystemRuntimeResponse.ContentProviderDiagnosticEvent(
				eventType,
				observedAt,
				mapContext(event.get("context")));
	}

	private Map<String, String> mapContext(Object value) {
		if (!(value instanceof Map<?, ?> context)) {
			return Map.of();
		}
		return context.entrySet()
				.stream()
				.sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
				.collect(Collectors.toMap(
						entry -> String.valueOf(entry.getKey()),
						entry -> stringValue(entry.getValue()),
						(left, right) -> left,
						LinkedHashMap::new));
	}

	private int asInt(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(value));
		}
		catch (NumberFormatException exception) {
			return 0;
		}
	}

	private String stringValue(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static String trimTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
