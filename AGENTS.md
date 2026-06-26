# 项目概述

ReelShort 是一个长期单机部署的聚合播放平台，包含 Android 原生 App、后台管理网站、Spring Boot 模块化单体后端、同机自部署 Flask ReelShort 内容源服务、PostgreSQL 和 Redis 数据层。

项目定位是聚合播放平台，不建设自有视频上传、转码和 CDN 分发链路。系统核心资产是用户体系、内容聚合、观看行为、积分账户、后台运营和内容源适配能力。

## 模块结构

- `android-app`：Android 原生客户端，规划使用 Kotlin、Jetpack Compose 和原生媒体播放能力。
- `admin-web`：后台管理网站，规划使用 Vue 和 Element Plus。
- `backend`：Spring Boot 模块化单体后端，按 `auth`、`user`、`content`、`watch`、`points`、`admin`、`system` 等业务模块组织。
- `content-provider`：第三方 ReelShort Flask API 的同机部署和适配层，Spring Boot 通过统一 `ContentProvider` 接口调用。
- `infra`：单机部署、进程管理、Nginx、PostgreSQL、Redis、日志和备份相关配置。
- `docs`：架构设计、接口说明、部署说明和阶段计划。

## 变更历史

[2026-06-26] auth/user - 增加普通用户注册登录、用户状态、BCrypt 密码哈希和 Auth API 文档。
[2026-06-26] docs - 同步内容源 404 与 502 错误分层说明，保持 API 文档与后端实现一致。
[2026-06-26] backend - 修复内容 API 错误分层，补充参数校验、内容源错误映射和 HTTP 客户端超时配置。
[2026-06-26] backend - 补齐 App 内容剧集列表和播放地址入口，继续保持 Spring Boot 作为唯一业务入口。
[2026-06-26] backend - 增加统一 API 响应、统一错误结构、请求 ID、内容源适配接口和内容搜索入口。
[2026-06-26] project - 增加 `.worktrees/` 忽略规则，用于后续隔离功能开发。
[2026-06-26] scaffold - 创建 backend、admin-web、android-app、content-provider、infra、docs 项目骨架。
[2026-06-26] project - 初始化 Git 仓库和项目基础目录，补充根 README 与忽略规则。
[2026-06-26] docs - 初始化项目说明和总体模块结构，补充 ReelShort 聚合播放平台架构边界。
