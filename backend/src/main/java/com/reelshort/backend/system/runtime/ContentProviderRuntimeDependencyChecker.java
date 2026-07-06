package com.reelshort.backend.system.runtime;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ContentProviderRuntimeDependencyChecker implements RuntimeDependencyChecker {

	private final RestClient restClient;
	private final String baseUrl;

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
			return "ok".equalsIgnoreCase(String.valueOf(status)) || "up".equalsIgnoreCase(String.valueOf(status))
					? RuntimeDependencyStatus.up("content-provider", diagnosticsDetail())
					: RuntimeDependencyStatus.down("content-provider", "unexpected health response");
		}
		catch (Exception exception) {
			return RuntimeDependencyStatus.down("content-provider", "unavailable");
		}
	}

	private String diagnosticsDetail() {
		try {
			Map<?, ?> response = restClient.get()
					.uri(baseUrl + "/diagnostics")
					.retrieve()
					.body(Map.class);
			Object diagnostics = response == null ? null : response.get("diagnostics");
			if (!(diagnostics instanceof Map<?, ?> diagnosticsMap)) {
				return "reachable; diagnostics unavailable";
			}
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
			return "reachable; diagnostics unavailable";
		}
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

	private static String trimTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
