# Android Player Cover And Error UX Design

## Goal

提升短剧播放器在视频准备、缓冲和失败场景的感知质量：用户进入播放器后先看到短剧封面和明确加载状态，而不是短暂黑屏；播放失败时有清晰的恢复操作，而不是只看到失败文案。

## Scope

- 仅修改 Android App 播放器 UI 和可测试的 UI contract。
- 不改后端 API、不改播放地址接口、不改积分上报、点赞收藏评论、选集切换和返回路径。
- Android 改动完成后必须编译、安装到模拟器并做关键路径手动验证。

## Recommended Approach

采用“封面背景 + 视频层渐进接管”的轻量方案：

- `PlayerScreen` 继续全屏沉浸式布局。
- `MediaPlayerSurface` 在首次 `STATE_READY` 前显示当前短剧 `coverUrl` 的全屏封面背景，并叠加暗色 scrim、加载文案和圆形进度。
- 视频 ready 后保留播放器层，隐藏封面/加载覆盖层，让 ExoPlayer 接管画面。
- `STATE_BUFFERING` 时不重新遮住整屏封面，仅展示轻量加载状态，避免正在播放时被大面积覆盖。
- `onPlayerError` 显示错误面板，保留返回、点赞/收藏、选集等操作层；错误面板提供重试、下一集和返回三个恢复动作。

## UX Details

- 加载文案复用当前语言：English 显示类似 `Loading EP 02...`；繁體中文显示 `正在載入第 02 集...`。
- 错误标题和说明使用本地化文案：说明原因保持克制，例如“视频加载失败，请稍后重试”。
- 重试按钮调用现有播放地址刷新/重新打开当前集流程；下一集按钮选择当前剧下一集，若没有下一集则禁用或隐藏；返回按钮沿用现有播放器返回路径。
- 所有按钮使用 Material Icons，触控区域不小于 48dp。
- 覆盖层避开右侧动作栏、底部进度/选集条和系统导航栏，不改变现有可点击区域。

## Data Flow

- `PlayerScreen` 从 `state.selectedBook?.coverUrl` 取得封面 URL，传给 `MediaPlayerSurface`。
- `MediaPlayerSurface` 内部继续监听 Media3 `Player.Listener`。
- `playableUrl == null`、首次 ready 前、`STATE_BUFFERING`、`STATE_IDLE` 或错误时显示加载/错误覆盖层。
- 错误操作：
  - `Retry`：调用传入的 `onRetryPlayback()`。
  - `Next`：调用传入的 `onOpenPlayer(nextEpisode)`。
  - `Back`：调用现有 `onBack()`。

## Testing

- Contract test 覆盖播放器覆盖层状态：
  - 首次 ready 前显示封面加载覆盖层。
  - ready 后即使 `isLoading=true` 也不显示大封面覆盖层。
  - error 状态展示错误面板和恢复动作。
  - 有下一集时显示下一集动作；最后一集不显示或禁用下一集动作。
- Android 验证：
  - `android-app\.\gradlew.bat :app-core:test :app:testDebugUnitTest :app:assembleDebug --no-daemon`
  - `adb install -r android-app\app\build\outputs\apk\debug\app-debug.apk`
  - 模拟器手动验收首页进播放器、加载封面、切集、播放失败恢复入口、返回路径。
