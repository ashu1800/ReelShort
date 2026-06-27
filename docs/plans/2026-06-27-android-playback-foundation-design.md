# Android 播放基础设计

## 背景

当前 Android App 已经能通过 `AppStateController` 获取首页、搜索、剧集、播放地址、观看记录和积分数据，但播放页仍主要依赖 `selectedEpisode` 与 `currentVideoUrl` 两个松散字段。后续接入 Android 原生 HLS 播放器时，需要一个稳定的纯 Kotlin 播放状态边界，让 Compose UI 和平台播放器只绑定状态，不直接处理业务接口细节。

用户最新优先级是先搞定 App 和数据来源，后端后续再扩展。因此本模块只调整 `android-app/app-core`，不新增后端复杂能力。

## 目标

- 在 `app-core` 建立可测试的播放状态模型。
- 打开分集后，统一记录当前剧集、分集、播放 URL、时长、播放位置和百分比。
- 本地播放进度更新不直接请求后端，避免播放器每秒触发网络调用。
- 上报观看进度时继续走现有 Spring Boot API，并同步刷新观看记录和积分。
- 支持播放地址刷新，便于后续处理 HLS 地址过期或播放器重试。

## 非目标

- 不接入 Android SDK、ExoPlayer 或 Media3。
- 不改 Flask 内容源真实解析逻辑。
- 不扩展后台管理、告警、支付或运维功能。
- 不改变后端 API 契约。

## 方案

新增 `PlaybackStatus` 与 `PlaybackState`，放在 `com.reelshort.app.state` 包内，作为 `AppUiState.playback` 的一部分。`PlaybackState` 保持纯数据模型，不依赖 Android 平台类型。

`AppStateController.openPlayer()` 成功获取 `VideoUrl` 后，将 `screen` 切到 `PLAYER`，并初始化 `playback` 为 `READY`。`updatePlaybackPosition()` 只更新本地状态，计算 `progressPercent`，后续平台播放器可以在播放 tick 中调用。`reportProgress()` 保持现有后端上报语义，但会同步更新 `playback.lastReportedPositionSeconds` 和 `lastReportedProgressPercent`。`refreshPlaybackUrl()` 重新调用现有 `loadVideoUrl()`，替换 URL，同时保留播放位置。

## 测试策略

只运行 `app-core` JVM 测试：

- 打开播放器后，`AppUiState.playback` 包含书籍、分集、URL、时长和 `READY` 状态。
- 本地进度更新会钳制非法值并计算百分比。
- 观看进度上报后刷新历史和积分，并记录已上报进度。
- 刷新播放地址时保留当前播放位置。
