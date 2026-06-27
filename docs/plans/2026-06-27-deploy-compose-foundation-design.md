# Deploy Compose Foundation Design

## Goal

补齐长期单机部署基础设施骨架，使 Spring Boot、Vue Admin、Flask 内容源、PostgreSQL、Redis 和 Nginx 能沿统一约定被 Docker Compose 管理。

## Scope

本阶段实现：

- 后端 Dockerfile。
- 后台 Web Dockerfile。
- 内容源 Dockerfile。
- Nginx 反向代理配置。
- `.env.example` 环境变量模板。
- 更新 `infra/docker-compose.yml` 使用环境变量、健康检查和内部端口。
- 更新部署文档。

本阶段不实现：

- 真实生产证书申请。
- 数据库迁移工具。
- 备份脚本自动化。
- 远程服务器发布。
- Docker 实机启动验证。本机当前未安装 Docker，只做文件级和构建级验证。

## Architecture

单机部署仍保留模块化单体边界：外部流量只进入 Nginx；Nginx 托管 Vue Admin 静态文件，并转发 `/api/` 到 Spring Boot。Spring Boot 访问 PostgreSQL、Redis 和 Flask 内容源服务。Flask 内容源不直接暴露给 App 或后台。

Docker Compose 负责本机进程编排，服务名作为容器内部 DNS 名称。配置通过 `.env` 注入，`.env.example` 只提供开发默认值和生产替换提示。

## Ports

- Nginx：`80:80`
- Backend：内部 `8080`，默认不直接暴露给公网。
- Content Provider：内部 `5000`，默认不直接暴露给公网。
- PostgreSQL：开发可映射 `5432:5432`。
- Redis：开发可映射 `6379:6379`。

## Verification

- `backend` 执行 `.\gradlew.bat test`。
- `admin-web` 执行 `npm run build`。
- `content-provider` 执行 `pytest`。
- 使用脚本检查 Dockerfile、Compose、Nginx 和 `.env.example` 关键文件存在。
- 本机没有 Docker 时不执行 `docker compose up`。
