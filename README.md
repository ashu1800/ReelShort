# ReelShort

ReelShort 是一个长期单机部署的聚合播放平台，包含 Android 原生 App、Vue 后台管理网站、Spring Boot 模块化单体后端、Flask 内容源服务、PostgreSQL 和 Redis。

## 模块

- `backend`：Spring Boot 核心业务服务。
- `admin-web`：Vue + Element Plus 后台管理网站。
- `android-app`：Kotlin + Jetpack Compose Android 客户端骨架。
- `content-provider`：Flask 内容源适配服务。
- `infra`：单机部署和基础设施配置。
- `docs`：接口、部署、计划文档。

## 当前阶段

当前已进入阶段 1 核心闭环建设。后端已具备基础系统健康检查、内容源适配入口、App 内容浏览入口，以及普通用户注册/登录基础能力；后续继续按 `开发文档.txt` 的阶段路线推进观看记录、积分和后台管理。
