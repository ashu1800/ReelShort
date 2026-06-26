package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class FlaskContentProviderTests {

	@Test
	void searchMapsFlaskResultsToProviderBooks() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(), requestTo("http://content-provider:5000/api/v1/reelshort/search?keywords=love"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "results": [
						    {
						      "book_id": "book-1",
						      "book_title": "Love Story",
						      "filtered_title": "love-story",
						      "book_pic": "https://example.com/cover.jpg",
						      "chapter_count": 12
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		List<ContentBook> books = provider.search("love");

		assertThat(books).containsExactly(new ContentBook(
				"book-1",
				"Love Story",
				"love-story",
				"https://example.com/cover.jpg",
				12));
		server.verify();
	}

	@Test
	void getEpisodesMapsFlaskEpisodeList() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(),
				requestTo("http://content-provider:5000/api/v1/reelshort/episodes/book-1?filtered_title=love-story"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "episodes": [
						    { "episode": 1, "chapter_id": "chapter-1" },
						    { "episode": 2, "chapter_id": "chapter-2" }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		List<ContentEpisode> episodes = provider.getEpisodes("book-1", "love-story");

		assertThat(episodes).containsExactly(
				new ContentEpisode(1, "chapter-1"),
				new ContentEpisode(2, "chapter-2"));
		server.verify();
	}

	@Test
	void getVideoUrlMapsFlaskVideoResponse() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(),
				requestTo("http://content-provider:5000/api/v1/reelshort/video/book-1/1?filtered_title=love-story&chapter_id=chapter-1"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "video_url": "https://cdn.example.com/video.m3u8",
						  "episode": 1,
						  "duration": 120,
						  "next_episode": {
						    "episode": 2,
						    "chapter_id": "chapter-2"
						  }
						}
						""", MediaType.APPLICATION_JSON));

		ContentVideo video = provider.getVideoUrl("book-1", 1, "love-story", "chapter-1");

		assertThat(video).isEqualTo(new ContentVideo(
				"https://cdn.example.com/video.m3u8",
				1,
				120,
				new ContentEpisode(2, "chapter-2")));
		server.verify();
	}

	@Test
	void upstreamHttpErrorBecomesContentProviderException() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(), requestTo("http://content-provider:5000/api/v1/reelshort/search?keywords=love"))
				.andExpect(method(GET))
				.andRespond(withServerError());

		assertThatThrownBy(() -> provider.search("love"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider returned 500");
		server.verify();
	}

	@Test
	void emptyResponseBodyBecomesContentProviderException() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(),
				requestTo("http://content-provider:5000/api/v1/reelshort/video/book-1/1?filtered_title=love-story&chapter_id=chapter-1"))
				.andExpect(method(GET))
				.andRespond(withSuccess("", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> provider.getVideoUrl("book-1", 1, "love-story", "chapter-1"))
				.isInstanceOf(ContentProviderException.class)
				.hasMessage("content provider returned empty response");
		server.verify();
	}
}
