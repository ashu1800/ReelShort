package com.reelshort.backend.social;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.reelshort.backend.social.SocialDtos.CommentResponse;

@Component
class SocialDisplayFallbacks {

	private static final OffsetDateTime BASE_COMMENT_TIME = OffsetDateTime.parse("2000-01-01T00:00:00Z");
	private static final List<String> USERNAME_PREFIXES = List.of(
			"viewer", "shortfan", "drama", "episode", "story", "binge");
	private static final List<String> COMMENTS = List.of(
			"Great episode.",
			"I need the next one.",
			"This plot is moving fast.",
			"Hooked already.",
			"The lead is carrying this.",
			"Did not expect that turn.",
			"Keeping this in my list.");

	long likeCount(String bookId, long realCount) {
		return realCount > 0 ? realCount : 128L + stableInt(bookId, "likes", 1_500);
	}

	long favoriteCount(String bookId, long realCount) {
		return realCount > 0 ? realCount : 36L + stableInt(bookId, "favorites", 700);
	}

	List<CommentResponse> comments(String bookId) {
		int count = 3 + stableInt(bookId, "comment-count", 3);
		int start = stableInt(bookId, "comment-start", COMMENTS.size());
		List<CommentResponse> results = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			results.add(new CommentResponse(
					stableUuid(bookId, index),
					username(bookId, index),
					COMMENTS.get((start + index) % COMMENTS.size()),
					BASE_COMMENT_TIME.plusMinutes(stableInt(bookId, "comment-time-" + index, 100_000)).toString()));
		}
		return results;
	}

	private String username(String bookId, int index) {
		String prefix = USERNAME_PREFIXES.get(stableInt(bookId, "username-prefix-" + index, USERNAME_PREFIXES.size()));
		int suffix = 10 + stableInt(bookId, "username-suffix-" + index, 90);
		return prefix + suffix;
	}

	private UUID stableUuid(String bookId, int index) {
		return UUID.nameUUIDFromBytes(("social-fallback:" + bookId + ":" + index)
				.getBytes(StandardCharsets.UTF_8));
	}

	private int stableInt(String bookId, String salt, int bound) {
		return Math.floorMod(Objects.hash(bookId, salt), bound);
	}
}
