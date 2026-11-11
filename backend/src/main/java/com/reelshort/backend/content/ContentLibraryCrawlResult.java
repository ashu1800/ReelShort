package com.reelshort.backend.content;

/**
 * 视频库一轮抓取的结果。
 *
 * @param added 本轮新增书目数（= 抓取后行数 - 抓取前行数，重复书不计入）
 * @param fetchedPages 本轮实际请求的目录页数（含每轮必拉的 page 0）
 * @param total 抓取结束后视频库总书目数
 * @param nextPage 下一轮从哪一页继续；目录抓完后停在 176，此后每轮只抓 page 0
 * @param hasMore 最后一页是否还有下一批
 */
public record ContentLibraryCrawlResult(int added, int fetchedPages, long total, int nextPage, boolean hasMore) {
}
