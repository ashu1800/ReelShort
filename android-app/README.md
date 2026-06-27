# ReelShort Android App

Android 原生客户端骨架，规划使用 Kotlin、Jetpack Compose 和 Android 原生媒体播放能力。

当前已建立阶段 1 的核心页面边界：

- 登录页：本地模拟账号密码状态，后续替换为 Spring Boot 登录接口。
- 首页：推荐内容列表占位，后续对接 Spring Boot 首页货架接口。
- 搜索页：本地搜索占位，后续对接 Spring Boot 搜索接口。
- 详情页：剧集信息和分集列表占位，后续对接 Spring Boot 剧集详情接口。
- 播放页：HLS 播放器区域占位，后续接入 Android 原生媒体播放方案。
- 观看记录、积分、订单页：保留后续核心闭环和商业化接口边界。

App 后续只访问 Spring Boot API，不直接访问 Flask 内容源服务。

当前机器未配置 Android SDK，因此本阶段只创建标准项目结构，不执行 Android 编译。

## 后续开发环境

- 安装 Android Studio。
- 安装 Android SDK 和 Android SDK Build-Tools。
- 在 `local.properties` 中配置 `sdk.dir`，或由 Android Studio 自动生成。

