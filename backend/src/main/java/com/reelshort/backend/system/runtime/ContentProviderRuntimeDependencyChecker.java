package com.reelshort.backend.system.runtime;

import java.util.Map;

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
					? RuntimeDependencyStatus.up("content-provider", "reachable")
					: RuntimeDependencyStatus.down("content-provider", "unexpected health response");
		}
		catch (Exception exception) {
			return RuntimeDependencyStatus.down("content-provider", "unavailable");
		}
	}

	private static String trimTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
