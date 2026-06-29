package com.reelshort.backend.content;

import java.util.List;
import java.util.Optional;

/**
 * 分集列表查询结果，附带内容源返回的书籍元信息。
 * 书籍元信息用于在没有详情缓存时回填，缺失时为 {@link Optional#empty()}。
 */
public record ContentEpisodesDetail(Optional<ContentBook> book, List<ContentEpisode> episodes) {

	public ContentEpisodesDetail {
		book = book == null ? Optional.empty() : book;
		episodes = episodes == null ? List.of() : List.copyOf(episodes);
	}
}
