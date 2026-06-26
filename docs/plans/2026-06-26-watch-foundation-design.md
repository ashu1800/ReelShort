# Watch Foundation Design

## 目标

建立阶段 1 的观看记录基础：App 上报单集播放进度，后端按当前用户保存最新观看状态，并提供最近观看历史查询。该模块为后续阶段式积分奖励提供可信的观看事件和进度数据来源。

## 范围

本阶段实现：

- `POST /api/app/watch/progress`
- `GET /api/app/watch/history`
- 观看记录按当前登录用户隔离。
- 同一用户、同一 `bookId`、同一 `episodeNum` 反复上报时更新同一条记录。
- 记录 `bookId`、`bookTitle`、`filteredTitle`、`episodeNum`、`chapterId`、`positionSeconds`、`durationSeconds`、`progressPercent`、`updatedAt`。

本阶段不实现积分发放、不实现观看阶段奖励记录、不实现后台查询。这些能力在 `points` 和 `admin` 子模块中扩展。

## 架构

`watch` 模块包含 JPA 实体、仓储、服务和 App 控制器。控制器只接收当前用户和请求数据，业务规则放在 `WatchService`。

`WatchRecord` 使用用户 ID、`bookId`、`episodeNum` 建立唯一约束，保证上报幂等更新。进度百分比由后端根据 `positionSeconds / durationSeconds` 计算并截断到 `100`，不信任客户端传入百分比。

## 错误语义

- 未登录访问：由 `auth/security` 返回 `401`。
- 请求字段缺失或空白：`400`。
- `positionSeconds < 0`：`400`。
- `durationSeconds <= 0`：`400`。
- `positionSeconds > durationSeconds` 时按 `durationSeconds` 计算并保存，避免客户端超报导致异常。

## 测试

- 未登录上报观看进度返回 `401`。
- 登录用户上报进度成功并返回计算后的百分比。
- 重复上报同一集更新同一条记录。
- 不同用户同一集记录隔离。
- 历史列表按最近更新时间倒序。
- 参数错误返回统一 `400`。
