-- 简介补抓的"已尝试"标记。
--
-- TwistyTV 的 NetShort(`ns_`) 书目在详情页 payload 里**根本没有 description**
-- （show 只有 id/title/posterUrl/category/episodeCount/genres/tags/views），
-- 只有 DramaBox / ReelShort / FreeReels 才有。补抓任务如果只看"简介为空"，
-- 会每轮把这批永远补不到的书重翻一遍（150 本 / 10 分钟，纯属打上游）。
-- 记下"已经尝试过"，下次只补没试过的；30 天后允许重试一次（上游可能补数据）。
ALTER TABLE content_book_cache ADD COLUMN description_checked_at TIMESTAMP WITH TIME ZONE NULL;
