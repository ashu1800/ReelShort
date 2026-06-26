package com.reelshort.backend.content;

public record ContentVideo(
		String videoUrl,
		int episode,
		int duration,
		ContentEpisode nextEpisode) {
}

