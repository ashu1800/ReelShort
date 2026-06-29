package com.reelshort.backend.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
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
						      "description": "A dramatic short series.",
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
				"A dramatic short series.",
				12));
		server.verify();
	}

	@Test
	void getShelfMapsRecommendResults() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(), requestTo("http://content-provider:5000/api/v1/reelshort/recommend"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "books": [
						    {
						      "book_id": "book-1",
						      "book_title": "Recommended",
						      "filtered_title": "recommended",
						      "book_pic": "https://example.com/recommended.jpg",
						      "description": "Recommended description.",
						      "chapter_count": 8
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		List<ContentBook> books = provider.getShelf(ContentShelfType.RECOMMEND);

		assertThat(books).containsExactly(new ContentBook(
				"book-1",
				"Recommended",
				"recommended",
				"https://example.com/recommended.jpg",
				"Recommended description.",
				8));
		server.verify();
	}

	@Test
	void getShelfMapsNewReleaseResults() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(), requestTo("http://content-provider:5000/api/v1/reelshort/newrelease"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "books": [
						    {
						      "book_id": "book-new",
						      "book_title": "New Release",
						      "filtered_title": "new-release",
						      "book_pic": "https://example.com/new.jpg",
						      "chapter_count": 3
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		List<ContentBook> books = provider.getShelf(ContentShelfType.NEW_RELEASE);

		assertThat(books).extracting(ContentBook::bookId).containsExactly("book-new");
		server.verify();
	}

	@Test
	void getShelfMapsDramaDubResults() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(), requestTo("http://content-provider:5000/api/v1/reelshort/dramadub"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "books": [
						    {
						      "book_id": "book-dub",
						      "book_title": "Drama Dub",
						      "filtered_title": "drama-dub",
						      "book_pic": "https://example.com/dub.jpg",
						      "chapter_count": 5
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		List<ContentBook> books = provider.getShelf(ContentShelfType.DRAMA_DUB);

		assertThat(books).extracting(ContentBook::bookId).containsExactly("book-dub");
		server.verify();
	}

	@Test
	void getEpisodesDetailMapsFlaskEpisodesAndBook() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(),
				requestTo("http://content-provider:5000/api/v1/reelshort/episodes/book-1?filtered_title=love-story"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "book": {
						    "book_id": "book-1",
						    "book_title": "Love Story",
						    "filtered_title": "love-story",
						    "book_pic": "https://example.com/cover.jpg",
						    "description": "A dramatic short series.",
						    "chapter_count": 12
						  },
						  "episodes": [
						    { "episode": 1, "chapter_id": "chapter-1", "title": "Opening Trap", "description": "A deal goes wrong." },
						    { "episode": 2, "chapter_id": "chapter-2", "title": "Second Move", "description": "The secret spreads." }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		ContentEpisodesDetail detail = provider.getEpisodesDetail("book-1", "love-story");

		assertThat(detail.episodes()).containsExactly(
				new ContentEpisode(1, "chapter-1", "Opening Trap", "A deal goes wrong."),
				new ContentEpisode(2, "chapter-2", "Second Move", "The secret spreads."));
		assertThat(detail.book()).contains(new ContentBook(
				"book-1",
				"Love Story",
				"love-story",
				"https://example.com/cover.jpg",
				"A dramatic short series.",
				12));
		server.verify();
	}

	@Test
	void getEpisodesDetailReturnsEmptyBookWhenAbsent() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(),
				requestTo("http://content-provider:5000/api/v1/reelshort/episodes/book-1?filtered_title=love-story"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "episodes": [
						    { "episode": 1, "chapter_id": "chapter-1", "title": "Opening Trap", "description": "A deal goes wrong." }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		ContentEpisodesDetail detail = provider.getEpisodesDetail("book-1", "love-story");

		assertThat(detail.book()).isEmpty();
		assertThat(detail.episodes()).hasSize(1);
		server.verify();
	}

	@Test
	void getEpisodesDetailTreatsBlankBookIdAsAbsentBook() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(),
				requestTo("http://content-provider:5000/api/v1/reelshort/episodes/book-1?filtered_title=love-story"))
				.andExpect(method(GET))
				.andRespond(withSuccess("""
						{
						  "book": { "book_id": "", "book_title": "", "filtered_title": "love-story" },
						  "episodes": []
						}
						""", MediaType.APPLICATION_JSON));

		ContentEpisodesDetail detail = provider.getEpisodesDetail("book-1", "love-story");

		assertThat(detail.book()).isEmpty();
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
						    "chapter_id": "chapter-2",
						    "title": "Second Move",
						    "description": "The secret spreads."
						  }
						}
						""", MediaType.APPLICATION_JSON));

		ContentVideo video = provider.getVideoUrl("book-1", 1, "love-story", "chapter-1");

		assertThat(video).isEqualTo(new ContentVideo(
				"https://cdn.example.com/video.m3u8",
				1,
				120,
				new ContentEpisode(2, "chapter-2", "Second Move", "The secret spreads.")));
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
	void upstreamNotFoundBecomesNotFoundContentProviderException() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ContentProvider provider = FlaskContentProvider.fromRestClient(builder.build(), "http://content-provider:5000");

		server.expect(once(), requestTo("http://content-provider:5000/api/v1/reelshort/search?keywords=missing"))
				.andExpect(method(GET))
				.andRespond(withResourceNotFound());

		assertThatThrownBy(() -> provider.search("missing"))
				.isInstanceOf(ContentProviderException.class)
				.extracting("statusCode")
				.isEqualTo(404);
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
