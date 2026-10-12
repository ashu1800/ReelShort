package com.reelshort.backend.content;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * 货架刷新的 single-flight 去重（P1-7）。
 *
 * <p>未鉴权的首页/货架接口在缓存缺失时会触发刷新，而货架刷新会带动片库扩容
 * （content-provider 侧硬上限 200 次上游请求）。同一 {@code (shelfType, locale)} 的并发
 * miss 只允许跑一次上游，其余并发调用复用同一个结果（含异常），不再重复打上游、
 * 也不再重复落库。
 *
 * <p>实现要点：
 * <ul>
 * <li>map 登记“本轮刷新的 {@link CompletableFuture}”，持有它的线程才执行任务；
 * 等待者最后统一 {@code join} 这个 future，因此一定拿到与持有者相同的结果/异常。</li>
 * <li>持有者的执行块放在 {@code synchronized (future)} 内并二次检查 {@code isDone()}，
 * 因此每个 future 只会被执行一次，同时避开了
 * {@link ConcurrentHashMap#computeIfAbsent} 映射函数里修改同一个 map 的写法
 * （那样会抛 {@code IllegalStateException: Recursive update}）。</li>
 * <li>持有者任务结束后 {@code remove(key, future)}，但 future 已 {@code complete}，
 * 在它被摘除前拿到引用的后来者仍能立即复用结果，不会补打上游；map 也不会无界增长。</li>
 * </ul>
 */
@Component
class ShelfSingleFlight {

	private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

	<T> T run(ContentShelfType shelfType, ContentLocale locale, Supplier<T> task) {
		String key = shelfType.name() + "::" + locale.name();
		CompletableFuture<Object> future = inFlight.computeIfAbsent(key, ignored -> new CompletableFuture<>());
		if (!future.isDone()) {
			synchronized (future) {
				if (!future.isDone()) {
					try {
						future.complete(task.get());
					}
					catch (RuntimeException | Error throwable) {
						future.completeExceptionally(throwable);
					}
					finally {
						inFlight.remove(key, future);
					}
				}
			}
		}
		try {
			@SuppressWarnings("unchecked")
			T result = (T) future.join();
			return result;
		}
		catch (CompletionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("content shelf refresh failed", cause);
		}
	}

	/**
	 * 仅供测试与诊断：当前仍在执行中的货架刷新数量。
	 */
	int inFlightCount() {
		return inFlight.size();
	}
}
