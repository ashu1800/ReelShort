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

	@Test
	void exposesLatestStructuredDiagnosticsWhenAvailable() {
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
						    "total_events": 3,
						    "counters": {
						      "catalog_search_empty": 2,
						      "video_url_missing": 1
						    },
						    "recent_events": [
						      {
						        "event_type": "video_url_missing",
						        "observed_at": "2026-07-06T10:10:00Z",
						        "context": {
						          "book_id": "book-1",
						          "episode_num": 2
						        }
						      }
						    ]
						  }
						}
						""", MediaType.APPLICATION_JSON));
		ContentProviderRuntimeDependencyChecker checker = new ContentProviderRuntimeDependencyChecker(
				builder,
				"http://content-provider:5000");

		checker.check();

		assertThat(checker.latestDiagnostics()).isNotNull();
		assertThat(checker.latestDiagnostics().totalEvents()).isEqualTo(3);
		assertThat(checker.latestDiagnostics().counters()).containsEntry("catalog_search_empty", 2);
		assertThat(checker.latestDiagnostics().counters()).containsEntry("video_url_missing", 1);
		assertThat(checker.latestDiagnostics().recentEvents()).hasSize(1);
		assertThat(checker.latestDiagnostics().recentEvents().get(0).eventType()).isEqualTo("video_url_missing");
		assertThat(checker.latestDiagnostics().recentEvents().get(0).context()).containsEntry("book_id", "book-1");
		assertThat(checker.latestDiagnostics().recentEvents().get(0).context()).containsEntry("episode_num", "2");
		server.verify();
	}

	@Test
	void clearsStructuredDiagnosticsWhenHealthIsNotUp() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo("http://content-provider:5000/health"))
				.andRespond(withSuccess("""
						{"status":"UP","service":"reelshort-content-provider"}
						""", MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo("http://content-provider:5000/diagnostics"))
				.andRespond(withSuccess("""
						{
						  "diagnostics": {
						    "total_events": 1,
						    "counters": {"search_empty": 1},
						    "recent_events": []
						  }
						}
						""", MediaType.APPLICATION_JSON));
		server.expect(once(), requestTo("http://content-provider:5000/health"))
				.andRespond(withSuccess("""
						{"status":"DOWN","service":"reelshort-content-provider"}
						""", MediaType.APPLICATION_JSON));
		ContentProviderRuntimeDependencyChecker checker = new ContentProviderRuntimeDependencyChecker(
				builder,
				"http://content-provider:5000");
		checker.check();
		assertThat(checker.latestDiagnostics()).isNotNull();

		RuntimeDependencyStatus status = checker.check();

		assertThat(status.status()).isEqualTo("DOWN");
		assertThat(checker.latestDiagnostics()).isNull();
		server.verify();
	}
}
