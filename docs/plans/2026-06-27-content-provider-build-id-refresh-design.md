# 内容源 Build ID 自动恢复设计

## 背景

`content-provider` 通过 ReelShort 的 Next.js `_next/data/{buildId}/{siteId}/...json` 数据接口获取搜索、货架、剧集和播放地址。`REELSHORT_NEXT_BUILD_ID` 未配置时，服务会从站点页面发现 build id 并缓存在内存中。

Next.js 站点发布后，旧 build id 对应的数据路径可能返回 404。如果 Flask 服务一直复用旧 build id，App 侧搜索、剧集或播放地址会持续失败，直到服务重启或手动更新环境变量。

## 目标

- 当 `_next/data` 请求因为旧 build id 返回 404 时，自动清空缓存 build id。
- 重新从站点页面发现最新 build id。
- 使用新 build id 对同一个数据请求重试一次。
- 如果重试后仍 404，继续按现有 404 错误返回，不吞掉真实缺失内容。

## 非目标

- 不新增后端接口。
- 不改变 Spring Boot 与 Flask 之间的 JSON 契约。
- 不引入持久化缓存或分布式协调。
- 不处理所有上游异常的通用重试，只针对 build id 失效这一类 404。

## 方案

在 `ReelShortClient._get_data()` 中区分“配置固定 build id”和“自动发现 build id”：

- 如果 `REELSHORT_NEXT_BUILD_ID` 显式配置，则认为调用方要固定版本，不自动刷新。
- 如果 build id 来自自动发现，数据请求 404 时将 `self.build_id` 置空，重新调用 `_discover_build_id()`，然后重试一次原请求。
- 重试只发生一次，避免真实路径错误时循环请求。

## 测试策略

使用 monkeypatch 替换 `requests.get`，模拟：

- 第一次数据请求使用旧 build id 返回 404。
- 重新发现页面返回新 build id。
- 第二次数据请求使用新 build id 返回正常 JSON。
- 显式配置 build id 时，404 不触发重新发现。
