package com.reelshort.backend.content;

/**
 * P1-7：货架 single-flight 的共享任务需要在“每个等待者各自独立”的写事务里落库，
 * 通过该 provider 每次取用全新的 {@link ContentCacheWriter} 事务代理。
 */
@FunctionalInterface
public interface ContentCacheWriterProvider {

	ContentCacheWriter get();
}
