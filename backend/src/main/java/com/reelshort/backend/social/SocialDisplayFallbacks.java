package com.reelshort.backend.social;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

import com.reelshort.backend.social.SocialDtos.CommentResponse;

@Component
class SocialDisplayFallbacks {

	private static final OffsetDateTime BASE_COMMENT_TIME = OffsetDateTime.parse("2000-01-01T00:00:00Z");
	private static final HotnessProfile COLD_PROFILE = new HotnessProfile(HotnessTier.COLD, 80L, 301, 15L, 46, 3, 10);
	private static final HotnessProfile WARM_PROFILE = new HotnessProfile(HotnessTier.WARM, 420L, 561, 40L, 101, 15, 31);
	private static final HotnessProfile HOT_PROFILE = new HotnessProfile(HotnessTier.HOT, 1_000L, 3_001, 120L, 301, 100, 121);
	private static final List<String> USERNAMES = List.of(
			"Mira",
			"NovaFan",
			"DramaLover",
			"SceneQueen",
			"StoryPulse",
			"BingeMood",
			"ShortFan",
			"EpisodeClub",
			"PlotWatcher",
			"ReelHeart",
			"Cliffhanger",
			"DailyDrama",
			"QuietViewer",
			"GoldenScene",
			"NextEpisode",
			"WatchMode",
			"TwistLover",
			"DramaNest",
			"StarryPlot",
			"ReelMood",
			"SceneHunter",
			"StoryBloom",
			"FreshEpisode",
			"DramaSpark");
	private static final List<String> COMMENTS = List.of(
			"Great episode.",
			"I need the next one.",
			"This plot is moving fast.",
			"Hooked already.",
			"The lead is carrying this.",
			"Did not expect that turn.",
			"Keeping this in my list.",
			"That ending was too much.",
			"Watching this straight through.",
			"Commenting before the next episode.",
			"Can't stop now.",
			"Replay worthy.");

	long likeCount(String bookId, long realCount) {
		HotnessProfile profile = profile(bookId);
		return profile.likeBase() + stableInt(bookId, profile.tier().name() + ":likes", profile.likeSpread()) + realCount;
	}

	long favoriteCount(String bookId, long realCount) {
		HotnessProfile profile = profile(bookId);
		return profile.favoriteBase()
				+ stableInt(bookId, profile.tier().name() + ":favorites", profile.favoriteSpread())
				+ realCount;
	}

	List<CommentResponse> comments(String bookId) {
		return comments(bookId, ThreadLocalRandom.current().nextLong());
	}

	List<CommentResponse> comments(String bookId, long shuffleSeed) {
		HotnessProfile profile = profile(bookId);
		int count = profile.commentBase() + stableInt(bookId, profile.tier().name() + ":comment-count",
				profile.commentSpread());
		int start = stableInt(bookId, profile.tier().name() + ":comment-start", COMMENTS.size());
		List<CommentResponse> results = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			results.add(new CommentResponse(
					stableUuid(bookId, index),
					username(bookId, index, profile.tier()),
					COMMENTS.get((start + index) % COMMENTS.size()),
					BASE_COMMENT_TIME.plusMinutes(
							stableInt(bookId, profile.tier().name() + ":comment-time-" + index, 100_000)).toString()));
		}
		Collections.shuffle(results, new Random(shuffleSeed));
		return results;
	}

	/**
	 * P2-16：按页取伪评论。伪评论是展示层的稳定派生数据（不落库），这里只保留当前页需要的条数，
	 * 避免热门剧每次请求都先构造最多 300 条再截断。同一 {@code (bookId, page, size)} 使用同一随机种子，
	 * 保证同一页多次请求顺序一致。
	 */
	List<CommentResponse> commentsPage(String bookId, int offset, int size, long shuffleSeed) {
		if (size <= 0) {
			return List.of();
		}
		List<CommentResponse> all = comments(bookId, shuffleSeed);
		if (offset >= all.size()) {
			return List.of();
		}
		return List.copyOf(all.subList(offset, Math.min(all.size(), offset + size)));
	}

	int commentCount(String bookId) {
		HotnessProfile profile = profile(bookId);
		return profile.commentBase() + stableInt(bookId, profile.tier().name() + ":comment-count",
				profile.commentSpread());
	}

	HotnessTier hotnessTier(String bookId) {
		return profile(bookId).tier();
	}

	private HotnessProfile profile(String bookId) {
		int bucket = stableInt(bookId, "hotness-tier", 100);
		if (bucket < 60) {
			return COLD_PROFILE;
		}
		if (bucket < 85) {
			return WARM_PROFILE;
		}
		return HOT_PROFILE;
	}

	private String username(String bookId, int index, HotnessTier tier) {
		return USERNAMES.get(stableInt(bookId, tier.name() + ":username-" + index, USERNAMES.size()));
	}

	private UUID stableUuid(String bookId, int index) {
		return UUID.nameUUIDFromBytes(("social-fallback:" + bookId + ":" + index)
				.getBytes(StandardCharsets.UTF_8));
	}

	private int stableInt(String bookId, String salt, int bound) {
		return Math.floorMod(Objects.hash(bookId, salt), bound);
	}

	enum HotnessTier {
		COLD,
		WARM,
		HOT
	}

	record HotnessProfile(HotnessTier tier, long likeBase, int likeSpread, long favoriteBase, int favoriteSpread,
			int commentBase, int commentSpread) {
	}
}
