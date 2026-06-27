# Android 播放页状态绑定设计

## 背景

`app-core` 已提供 `PlaybackState`，但 Compose 播放页仍直接读取旧的 `currentVideoUrl` 字段，只提供一个固定“上报 75% 观看进度”的按钮。这样平台播放器后续接入时无法通过 UI 层更新播放位置、刷新播放地址，也无法按当前播放进度上报。

当前机器没有 Android SDK 和 `local.properties`，所以本模块不声明 Android UI 编译通过；可验证部分放在 `app-core` 单元测试和源码审查。

## 目标

- 播放页使用 `AppUiState.playback` 作为主要展示数据。
- 播放页展示播放地址、时长、当前位置、进度百分比和已上报进度。
- 播放页提供模拟进度按钮，用于在无平台播放器时验证 `updatePlaybackPosition()` 链路。
- 播放页提供刷新播放地址按钮，调用 `refreshPlaybackUrl()`。
- 播放页上报按钮按当前 `PlaybackState.positionSeconds/durationSeconds` 上报，不再固定 75%。

## 非目标

- 不接入 Media3、ExoPlayer 或真实 HLS 播放器。
- 不改后端、内容源或 API 契约。
- 不声明当前机器能构建 Android `app` 模块。

## 方案

`ReelShortApp` 将 `actions.updatePlaybackPosition()` 和 `actions.refreshPlaybackUrl()` 传入 `MainShell`。`MainShell` 将完整 `AppUiState` 传给 `PlayerScreen`。`PlayerScreen` 读取 `state.playback`，渲染播放器占位、当前播放元数据、进度信息和三个操作：

- 模拟播放 25%。
- 刷新播放地址。
- 上报当前进度。

如果尚未进入 `READY` 状态，则禁用刷新和上报。

## 验证策略

- `app-core` 测试覆盖 action facade 的播放操作委托。
- 使用源码审查确认 `PlayerScreen` 不再依赖 `currentVideoUrl` 旧入参。
- 运行 `android-app :app-core:test`、`content-provider pytest` 和 `git diff --check`。
- 记录 Android UI 模块未编译的环境限制。
