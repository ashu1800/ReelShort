# Android Media3 播放器基础层设计

## 背景

App 播放页当前已经能从 Spring Boot 获取播放地址、维护 `PlaybackState`、刷新地址和上报进度，但视频区域仍是“HLS 播放器待接入”的静态占位。要让 App 形成真实短剧观看闭环，需要先接入 Android 原生播放器容器。

## 目标

- 使用 AndroidX Media3 ExoPlayer 播放后端返回的 HLS/媒体 URL。
- 播放页仍只消费 Spring Boot 返回的 `VideoUrl`，不直连 Flask 内容源。
- 保留当前播放地址刷新、模拟进度和上报进度入口。
- 增加可测试的播放 URL 判定 helper，避免空 URL 或非 HTTP URL 初始化播放器。
- 先建立平台播放器边界，不扩展后台或后端接口。

## 非目标

- 不做 DRM、投屏、离线下载、倍速、字幕、清晰度切换。
- 不做播放器进度自动轮询上报；本阶段保留现有手动/模拟进度入口。
- 不改变 `app-core` 的 `PlaybackState`、`VideoUrl` 模型。

## 技术选择

采用 AndroidX Media3：

- `androidx.media3:media3-exoplayer`
- `androidx.media3:media3-ui`

Compose 通过 `AndroidView` 嵌入 `PlayerView`。播放器生命周期限定在 `MediaPlayerSurface(videoUrl)` composable 内，URL 变化时重新设置 `MediaItem`，组件离开组合时释放播放器。

## 组件方案

- `playableMediaUrlOrNull(url)`：trim 后只接受 `http://` 或 `https://` URL。
- `MediaPlayerSurface(videoUrl, episodeNumber)`：
  - URL 可播放：创建 `ExoPlayer`，绑定 `PlayerView`。
  - URL 不可播放：展示当前风格的播放器占位/待加载状态。
- `PlayerScreen`：
  - 将静态播放器 Box 替换为 `MediaPlayerSurface`。
  - 保留标题、播放地址、刷新和进度上报区域。

## 验收标准

- 有合法 HTTP/HLS URL 时播放页创建 Media3 播放器容器。
- 空 URL 或非法 URL 不初始化播放器，仍显示清晰占位。
- JVM 单测覆盖 URL 判定。
- APK 能构建并在雷电模拟器启动，无 `FATAL EXCEPTION`。
- App 仍只从 Spring Boot API 获取播放 URL。
