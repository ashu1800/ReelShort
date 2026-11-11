package com.reelshort.backend.content;

import java.util.List;

/**
 * 后台视频库分页结果。
 *
 * @param page 修正后的页码（负数归 0）
 * @param size 修正后的每页条数（1..100）
 */
public record ContentBookPageResponse(List<ContentBook> books, long total, int page, int size) {
}
