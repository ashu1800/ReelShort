# Android App Foundation Design

## Goal

把 Android App 从占位文本升级为阶段 1 核心播放闭环的 UI 和状态骨架，为后续接入 Spring Boot API、播放器和本地会话持久化提供稳定结构。

## Scope

本阶段实现：

- Compose 单 Activity App shell。
- 登录、首页、搜索、剧集详情、播放、观看历史、积分、订单页面骨架。
- 本地 `AppState` 和示例数据，模拟页面流转。
- 明确所有数据只来自未来 Spring Boot API，不直连 Flask。
- README 记录 Android SDK 缺失导致本阶段不执行 Android 编译。

本阶段不实现：

- 真实网络请求。
- HLS 播放器。
- Token 持久化。
- Android 编译验证。
- 后台推送、下载、支付。

## Architecture

当前保持单模块 Android 工程。`MainActivity` 只启动 Compose 根组件；UI 状态集中在 `AppState`，页面通过显式事件修改状态。后续接入网络时，可把示例数据替换为 Repository/ViewModel，不改变页面和导航边界。

App 层只面向 Spring Boot API：登录注册、首页推荐、搜索、剧集详情/分集、播放地址、观看进度、积分和订单都通过后端接口。播放页当前只展示播放地址占位和进度上报入口，后续接 Android 原生媒体播放能力。

## Screens

- `Login`：账号密码输入和登录入口。
- `Home`：推荐内容与新剧入口。
- `Search`：搜索输入和结果列表。
- `Detail`：剧集信息、分集列表、继续播放。
- `Player`：播放占位、进度信息、上报进度按钮。
- `History`：最近观看记录。
- `Points`：积分余额和流水。
- `Orders`：充值订单预留列表。

## Verification

本机未配置 Android SDK，因此不执行 `gradlew assemble`。本阶段验证：

- Kotlin 源文件存在且只使用已声明 Compose/Material3 依赖。
- `rg` 检查核心页面、状态和事件函数存在。
- 文档同步说明当前验证限制。
