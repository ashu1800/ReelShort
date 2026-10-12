package com.reelshort.backend.social;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "comments")
public class Comment {

	/**
	 * 上次分配的创建时间（纳秒）。
	 *
	 * <p>评论列表按 {@code created_at desc} 分页，而同一毫秒内写入的多条评论在
	 * 时间戳完全相同时排序是未定义的（数据库可能按主键顺序返回），既会让"最新在前"随机翻转，
	 * 也会让相邻两页出现重复或漏读。这里保证同一 JVM 内分配的时间戳严格递增：
	 * 正常情况下就是墙上时钟，只有时钟精度不足（或回拨）时才对上一条 +1 纳秒。
	 */
	private static final AtomicLong LAST_CREATED_AT_NANOS = new AtomicLong();

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "username", nullable = false, length = 64)
	private String username;

	@Column(name = "book_id", nullable = false, length = 128)
	private String bookId;

	@Column(name = "content", nullable = false, length = 500)
	private String content;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected Comment() {
	}

	private Comment(UUID id, UUID userId, String username, String bookId, String content) {
		this.id = id;
		this.userId = userId;
		this.username = username;
		this.bookId = bookId;
		this.content = content;
		this.createdAt = nextCreatedAt();
	}

	/**
	 * 分配严格递增的创建时间：以墙上时钟为准，重复或回拨时在上一条基础上 +1 微秒。
	 *
	 * <p>步进取 1 微秒而不是 1 纳秒，因为 PostgreSQL `timestamptz` 与 H2 的时间戳都只保留到
	 * 微秒：纳秒级步进会被截断成同一个值，排序仍然并列。
	 */
	private static OffsetDateTime nextCreatedAt() {
		while (true) {
			long previous = LAST_CREATED_AT_NANOS.get();
			OffsetDateTime now = OffsetDateTime.now();
			long candidate = now.toEpochSecond() * 1_000_000_000L + now.getNano();
			long next = Math.max(candidate, previous + 1_000);
			if (LAST_CREATED_AT_NANOS.compareAndSet(previous, next)) {
				return OffsetDateTime.ofInstant(
						Instant.ofEpochSecond(Math.floorDiv(next, 1_000_000_000L),
								Math.floorMod(next, 1_000_000_000L)),
						ZoneOffset.UTC);
			}
		}
	}

	public static Comment create(UUID userId, String username, String bookId, String content) {
		return new Comment(UUID.randomUUID(), userId, username, bookId, content);
	}

	public UUID id() {
		return id;
	}

	public UUID userId() {
		return userId;
	}

	public String username() {
		return username;
	}

	public String bookId() {
		return bookId;
	}

	public String content() {
		return content;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}
}
