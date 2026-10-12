package com.reelshort.backend.content;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * P1-7：货架 single-flight 的共享任务需要在“每个等待者各自独立”的写事务里落库，
 * 因此这里按 prototype 提供 {@link ContentCacheWriter}，每次取用都能拿到全新的 Spring 事务代理。
 */
@Configuration
public class ContentCacheWriterConfiguration {

	@Bean
	@Scope("prototype")
	ContentCacheWriter contentCacheWriter(ContentShelfCacheRepository contentShelfCacheRepository,
			ContentBookCacheRepository contentBookCacheRepository,
			ContentEpisodeCacheRepository contentEpisodeCacheRepository,
			ContentVideoCacheRepository contentVideoCacheRepository,
			ContentEpisodeRuntimeCacheRepository contentEpisodeRuntimeCacheRepository, ObjectMapper objectMapper) {
		return new ContentCacheWriter(contentShelfCacheRepository, contentBookCacheRepository,
				contentEpisodeCacheRepository, contentVideoCacheRepository, contentEpisodeRuntimeCacheRepository,
				objectMapper);
	}

	@Bean
	ContentCacheWriterProvider contentCacheWriterProvider(ObjectProvider<ContentCacheWriter> writers) {
		return writers::getObject;
	}
}
