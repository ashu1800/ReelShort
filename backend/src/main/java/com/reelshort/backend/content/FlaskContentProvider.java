package com.reelshort.backend.content;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
public class FlaskContentProvider implements ContentProvider {

	private final RestClient restClient;
	private final String baseUrl;

	@Autowired
	public FlaskContentProvider(RestClient.Builder restClientBuilder,
			@Value("${reelshort.content-provider.base-url:http://127.0.0.1:5000}") String baseUrl) {
		this.restClient = restClientBuilder.build();
		this.baseUrl = trimTrailingSlash(baseUrl);
	}

	static FlaskContentProvider fromRestClient(RestClient restClient, String baseUrl) {
		return new FlaskContentProvider(restClient, baseUrl);
	}

	private FlaskContentProvider(RestClient restClient, String baseUrl) {
		this.restClient = restClient;
		this.baseUrl = trimTrailingSlash(baseUrl);
	}

	@Override
	public List<ContentBook> search(String keywords) {
		SearchResponse response = restClient.get()
				.uri(uri("/api/v1/reelshort/search").queryParam("keywords", keywords).toUriString())
				.retrieve()
				.body(SearchResponse.class);
		return response == null || response.results() == null
				? List.of()
				: response.results().stream().map(FlaskBook::toContentBook).toList();
	}

	@Override
	public List<ContentEpisode> getEpisodes(String bookId, String filteredTitle) {
		EpisodesResponse response = restClient.get()
				.uri(uri("/api/v1/reelshort/episodes/{bookId}")
						.queryParam("filtered_title", filteredTitle)
						.buildAndExpand(bookId)
						.toUriString())
				.retrieve()
				.body(EpisodesResponse.class);
		return response == null || response.episodes() == null
				? List.of()
				: response.episodes().stream().map(FlaskEpisode::toContentEpisode).toList();
	}

	@Override
	public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId) {
		FlaskVideo response = restClient.get()
				.uri(uri("/api/v1/reelshort/video/{bookId}/{episodeNum}")
						.queryParam("filtered_title", filteredTitle)
						.queryParam("chapter_id", chapterId)
						.buildAndExpand(bookId, episodeNum)
						.toUriString())
				.retrieve()
				.body(FlaskVideo.class);
		if (response == null) {
			throw new IllegalStateException("content provider returned empty video response");
		}
		return response.toContentVideo();
	}

	private UriComponentsBuilder uri(String path) {
		return UriComponentsBuilder.fromUriString(baseUrl).path(path);
	}

	private static String trimTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private record SearchResponse(List<FlaskBook> results) {
	}

	private record EpisodesResponse(List<FlaskEpisode> episodes) {
	}

	private record FlaskBook(
			@JsonProperty("book_id") String bookId,
			@JsonProperty("book_title") String bookTitle,
			@JsonProperty("filtered_title") String filteredTitle,
			@JsonProperty("book_pic") String bookPic,
			@JsonProperty("chapter_count") int chapterCount) {

		ContentBook toContentBook() {
			return new ContentBook(bookId, bookTitle, filteredTitle, bookPic, chapterCount);
		}
	}

	private record FlaskEpisode(
			int episode,
			@JsonProperty("chapter_id") String chapterId) {

		ContentEpisode toContentEpisode() {
			return new ContentEpisode(episode, chapterId);
		}
	}

	private record FlaskVideo(
			@JsonProperty("video_url") String videoUrl,
			int episode,
			int duration,
			@JsonProperty("next_episode") FlaskEpisode nextEpisode) {

		ContentVideo toContentVideo() {
			return new ContentVideo(videoUrl, episode, duration,
					nextEpisode == null ? null : nextEpisode.toContentEpisode());
		}
	}
}
