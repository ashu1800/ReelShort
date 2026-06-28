# Android Media3 播放进度同步设计

## 背景

播放页已经接入 Media3/ExoPlayer，并且 `app-core` 已具备 `PlaybackState`、本地进度更新和观看进度上报能力。但当前 UI 仍依赖“模拟 25%”按钮更新进度，真实播放器位置不会进入 `PlaybackState`，观看上报链路还没有和播放器形成闭环。

## 目标

- Media3 播放器有合法 URL 时，定时读取播放器当前位置和时长。
- 将毫秒级播放器状态转换为秒级状态，并调用现有 `onUpdatePlaybackPosition(positionSeconds, durationSeconds)`。
- 保留现有手动“上报当前进度”按钮，不在本阶段自动发放或自动上报积分。
- 保留“模拟 25%”作为调试入口，但文案调整为更明确的“同步 25%”。
- 不修改 `app-core` 的业务边界和后端接口。

## 非目标

- 不实现后台自动定时上报观看进度。
- 不监听所有播放器事件或做复杂播放状态机。
- 不实现播放完成自动跳下一集。
- 不引入 Compose UI 测试框架。

## 设计

- 增加 `mediaPositionSeconds(positionMs)` 和 `mediaDurationSeconds(playerDurationMs, fallbackDurationSeconds)` 纯函数。
- `MediaPlayerSurface` 增加参数：
  - `fallbackDurationSeconds`
  - `onProgress`
- 使用 `LaunchedEffect(player)` 每秒读取：
  - `player.currentPosition`
  - `player.duration`
- 当播放器 duration 未知时，使用后端 `VideoUrl.durationSeconds` 作为 fallback。
- 仅在可播放 URL 存在且 duration > 0 时同步，避免无意义状态更新。

## 验收标准

- JVM 单测覆盖毫秒到秒、负值归零、未知 duration fallback。
- 播放页 Media3 容器能把播放器进度同步给 `PlaybackState`。
- “上报当前进度”按钮可基于真实播放器同步后的 `PlaybackState` 启用。
- APK 能构建并在雷电模拟器启动，无 `FATAL EXCEPTION`。
