package com.reelshort.backend.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.reelshort.backend.system.runtime.ContentProviderRuntimeDependencyChecker;
import com.reelshort.backend.system.runtime.RuntimeDependencyStatus;

class ContentProviderRuntimeDependencyCheckerTests {

	@Test
	void checkIncludesProviderDiagnosticSummaryWhenAvailable() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo("http://content-provider:5000/health"))
				.andRespond(withSuccess("""
						{"status":"UP","service":"reelshort-content-provider"}
						""", MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo("http://content-provider:5000/diagnostics"))
				.andRespond(withSuccess("""
						{
						  "status": "UP",
						  "service": "reelshort-content-provider",
						  "diagnostics": {
						    "total_events": 2,
						    "counters": {
						      "search_empty": 1,
						      "next_data_404": 1
						    },
						    "recent_events": [
						      {
						        "event_type": "next_data_404",
						        "observed_at": "2026-07-06T10:00:00Z",
						        "context": {"data_path": "/search.json"}
						      }
						    ]
						  }
						}
						""", MediaType.APPLICATION_JSON));

		RuntimeDependencyStatus status = new ContentProviderRuntimeDependencyChecker(
				builder,
				"http://content-provider:5000").check();

		assertThat(status.status()).isEqualTo("UP");
		assertThat(status.detail()).contains("diagnostics events=2");
		assertThat(status.detail()).contains("search_empty=1");
		assertThat(status.detail()).contains("next_data_404=1");
		server.verify();
	}
}
