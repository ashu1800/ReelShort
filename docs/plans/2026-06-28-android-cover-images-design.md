# Android 内容封面图基础层设计

## 背景

Android App 已完成深色影院感视觉基础层，但首页、搜索结果和详情页仍使用文字渐变海报占位。后端与内容源已经传递 `BookSummary.coverUrl`，App 需要先把封面图展示出来，才能更接近真实短剧聚合产品。

## 目标

- 使用 Coil Compose 加载远程封面图。
- 增加 Android 网络权限，允许 App 加载图片和访问本地后端。
- 在首页、搜索结果和详情页展示真实封面。
- 保留现有渐变文字海报作为空 URL、加载失败和预览 fallback。
- 不改变 `app-core` 数据模型、后端接口和状态控制逻辑。

## 非目标

- 不实现图片预加载、磁盘策略自定义或复杂缓存调优。
- 不引入自定义图片服务。
- 不处理真实 HLS 播放器。
- 不改变内容源返回结构。

## 技术选择

采用 Coil 2.7：

- `io.coil-kt:coil-compose`

理由：

- Compose 原生组件 `AsyncImage` 简洁。
- 兼容当前项目的 Kotlin 2.0.21、AGP 8.7.3 和 `compileSdk 35`。
- 不需要手写图片下载、缓存和解码逻辑。

## 组件方案

- `coverUrlOrNull()`：将空白 URL 规整为 `null`，避免把空字符串交给图片加载器。
- `PosterBlock(title, coverUrl, modifier)`：统一海报组件。
  - `coverUrl == null`：展示渐变 fallback。
  - `coverUrl != null`：使用 `AsyncImage`，设置 `contentScale = Crop`。
  - 图片加载失败时显示渐变 fallback。
- `BookRow` 和 `BookHero` 只传入 `book.coverUrl`，其余 UI 结构不变。

## 验收标准

- `BookSummary.coverUrl` 非空时，首页、搜索和详情海报区域使用远程图。
- 空 URL 或加载失败时仍显示原有风格 fallback，不出现空白块。
- AndroidManifest 包含 `android.permission.INTERNET`。
- APK 能构建并在雷电模拟器启动，无 `FATAL EXCEPTION`。
