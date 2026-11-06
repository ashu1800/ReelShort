package com.reelshort.backend.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reelshort.backend.content.account.ReelshortAccountGateway;

@Component
public class FlaskContentProvider implements ContentProvider, ReelshortAccountGateway {

	/** P2-16：content-provider 的内部共享密钥请求头。 */
	static final String INTERNAL_TOKEN_HEADER = "X-Content-Provider-Token";

	private final RestClient restClient;
	private final String baseUrl;

	@Autowired
	public FlaskContentProvider(RestClient.Builder restClientBuilder,
			@Value("${reelshort.content-provider.base-url:http://127.0.0.1:5000}") String baseUrl,
			@Value("${reelshort.content-provider.internal-token:${CONTENT_PROVIDER_INTERNAL_TOKEN:}}")
			String internalToken) {
		// 内部 Token 未配置时不发送该请求头，保持本地开发可用；生产由部署环境注入。
		this.restClient = internalToken == null || internalToken.isBlank()
				? restClientBuilder.build()
				: restClientBuilder.defaultHeader(INTERNAL_TOKEN_HEADER, internalToken).build();
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
		try {
			FlaskVideo response = get(FlaskVideo.class,
					uri("/api/v1/reelshort/video/{bookId}/{episodeNum}")
							.queryParam("filtered_title", filteredTitle)
							.queryParam("chapter_id", chapterId)
							.queryParam("locale", "en")
							.buildAndExpand(bookId, episodeNum)
							.toUriString());
			return response.toContentVideo();
		}
		catch (ContentProviderException exception) {
			if (exception.statusCode() == 404) {
				// The upstream source only publishes play URLs for the chapters it
				// gives away; for a locked chapter it returns the page without a
				// video URL and the provider answers 404. Users used to be shown the
				// provider's internal wording ("content provider returned 404"), so
				// this path says what actually happened.
				throw new ContentProviderException(404, "This episode is not available yet", exception);
			}
			throw exception;
		}
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

	@Override
	public ReelshortAccountPushResult updateAccount(String email, String password) {
		AccountPushResponse response = post(AccountPushResponse.class, "/api/v1/reelshort/credentials",
				Map.of("email", email, "password", password));
		AccountPayload account = response.account();
		return new ReelshortAccountPushResult(response.authenticated(),
				account == null ? "" : account.emailMasked());
	}

	@Override
	public Optional<LiveStatus> liveStatus() {
		try {
			AccountSummary summary = get(AccountSummary.class,
					uri("/api/v1/reelshort/account").toUriString());
			AccountVip vip = summary.vip();
			boolean known = vip != null && vip.known();
			return Optional.of(new LiveStatus(summary.authenticated(), nullToEmpty(summary.emailMasked()), known,
					known && Boolean.TRUE.equals(vip.vipActive()), known ? vip.vipExpireAt() : null,
					known && Boolean.TRUE.equals(vip.vipRenew()), vip == null ? null : vip.coins()));
		}
		catch (ContentProviderException exception) {
			// 内容源不可达或还没配账号：让后台显示"未知"，不要当成"非会员"。
			return Optional.empty();
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private <T> T post(Class<T> responseType, String path, Object body) {
		try {
			T response = restClient.post()
					.uri(uri(path).toUriString())
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(responseType);
			if (response == null) {
				throw new ContentProviderException(502, "content provider returned empty response");
			}
			return response;
		}
		catch (RestClientResponseException exception) {
			throw new ContentProviderException(502, "content provider rejected the request", exception);
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

	private record AccountPushResponse(boolean authenticated, AccountPayload account) {
	}

	private record AccountPayload(String emailMasked, boolean configured, boolean authenticated) {
	}

	private record AccountSummary(String emailMasked, boolean configured, boolean authenticated, AccountVip vip) {
	}

	private record AccountVip(boolean known, Integer coins, Boolean vipActive, String vipExpireAt, Boolean vipRenew) {
	}
}
