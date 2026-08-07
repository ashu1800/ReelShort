package com.reelshort.backend.content;

public record ContentPlayResponse(
		String videoUrl,
		int episode,
		int duration,
		ContentEpisode nextEpisode,
		int estimatedRewardPoints) {

	public static ContentPlayResponse from(ContentVideo video, int estimatedRewardPoints) {
		return new ContentPlayResponse(video.videoUrl(), video.episode(), video.duration(), video.nextEpisode(),
				Math.max(estimatedRewardPoints, 0));
	}
}
