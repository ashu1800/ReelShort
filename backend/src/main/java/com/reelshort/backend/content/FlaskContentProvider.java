package com.reelshort.backend.content;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
	public List<ContentBook> search(String keywords, ContentLocale locale) {
		SearchResponse response = get(SearchResponse.class,
				uri("/api/v1/reelshort/search")
						.queryParam("keywords", keywords)
						.queryParam("locale", locale.apiValue())
						.toUriString());
		return response.results() == null
				? List.of()
				: response.results().stream().map(FlaskBook::toContentBook).toList();
	}

	@Override
	public List<ContentBook> getShelf(ContentShelfType shelfType, ContentLocale locale) {
		ShelfResponse response = get(ShelfResponse.class,
				uri(shelfType.providerPath()).queryParam("locale", locale.apiValue()).toUriString());
		return response.books() == null
				? List.of()
				: response.books().stream().map(FlaskBook::toContentBook).toList();
	}

	@Override
	public ContentEpisodesDetail getEpisodesDetail(String bookId, String filteredTitle, ContentLocale locale) {
		EpisodesResponse response = get(EpisodesResponse.class,
				uri("/api/v1/reelshort/episodes/{bookId}")
						.queryParam("filtered_title", filteredTitle)
						.queryParam("locale", locale.apiValue())
						.buildAndExpand(bookId)
						.toUriString());
		Optional<ContentBook> book = Optional.ofNullable(response.book())
				.map(FlaskBook::toContentBook)
				.filter(contentBook -> contentBook.bookId() != null && !contentBook.bookId().isBlank());
		List<ContentEpisode> episodes = response.episodes() == null
				? List.of()
				: response.episodes().stream().map(FlaskEpisode::toContentEpisode).toList();
		return new ContentEpisodesDetail(book, episodes);
	}

	@Override
	public ContentVideo getVideoUrl(String bookId, int episodeNum, String filteredTitle, String chapterId,
			ContentLocale locale) {
		FlaskVideo response = get(FlaskVideo.class,
				uri("/api/v1/reelshort/video/{bookId}/{episodeNum}")
						.queryParam("filtered_title", filteredTitle)
						.queryParam("chapter_id", chapterId)
						.queryParam("locale", locale.apiValue())
						.buildAndExpand(bookId, episodeNum)
						.toUriString());
		return response.toContentVideo();
	}

	private <T> T get(Class<T> responseType, String url) {
		try {
			T response = restClient.get()
					.uri(url)
					.retrieve()
					.body(responseType);
			if (response == null) {
				throw new ContentProviderException(502, "content provider returned empty response");
			}
			return response;
		}
		catch (RestClientResponseException exception) {
			if (exception.getStatusCode().value() == 404) {
				throw new ContentProviderException(404, "content provider returned 404", exception);
			}
			throw new ContentProviderException(502,
					"content provider returned " + exception.getStatusCode().value(), exception);
		}
		catch (ResourceAccessException exception) {
			throw new ContentProviderException(503, "content provider unavailable", exception);
		}
		catch (RestClientException exception) {
			throw new ContentProviderException(503, "content provider request failed", exception);
		}
	}

	private UriComponentsBuilder uri(String path) {
		return UriComponentsBuilder.fromUriString(baseUrl).path(path);
	}

	private static String trimTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private record SearchResponse(List<FlaskBook> results) {
	}

	private record ShelfResponse(List<FlaskBook> books) {
	}

	private record EpisodesResponse(FlaskBook book, List<FlaskEpisode> episodes) {
	}

	private record FlaskBook(
			@JsonProperty("book_id") String bookId,
			@JsonProperty("book_title") String bookTitle,
			@JsonProperty("filtered_title") String filteredTitle,
			@JsonProperty("book_pic") String bookPic,
			String description,
			@JsonProperty("chapter_count") int chapterCount) {

		ContentBook toContentBook() {
			return new ContentBook(bookId, bookTitle, filteredTitle, bookPic, description == null ? "" : description,
					chapterCount);
		}
	}

	private record FlaskEpisode(
			int episode,
			@JsonProperty("chapter_id") String chapterId,
			String title,
			String description) {

		ContentEpisode toContentEpisode() {
			return new ContentEpisode(episode, chapterId, title == null ? "" : title,
					description == null ? "" : description);
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
