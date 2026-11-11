-- 视频库：content_book_cache 从“货架快照的副产品”升级为持续增长的片库。
--
-- created_at 只用于后台分页排序（最新入库的排前面），旧行用 updated_at 回填；
-- 抓取进度放在单行状态表，定时任务按 next_page 一轮拉一页，只增不减。
ALTER TABLE content_book_cache ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
UPDATE content_book_cache SET created_at = updated_at;
ALTER TABLE content_book_cache ALTER COLUMN created_at SET NOT NULL;

CREATE TABLE content_library_crawl_state (
    id varchar(32) PRIMARY KEY,
    next_page integer NOT NULL,
    last_crawled_at TIMESTAMP WITH TIME ZONE,
    last_added integer NOT NULL DEFAULT 0,
    last_error varchar(1024),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO content_library_crawl_state (id, next_page, last_crawled_at, last_added, last_error, updated_at)
VALUES ('library', 1, NULL, 0, NULL, now());
