# 单机备份与恢复基础设计

> 历史设计说明：本文记录 2026-06-27 的初始方案，明文 `.env` 复制设计已被后续安全加固取代。当前默认不备份 `infra/.env`；确需配置灾备时仅允许在 Windows 使用 DPAPI `CurrentUser` 加密，并在受限安全目录恢复。请以 `docs/deploy/backup-restore.md` 和现行脚本行为为准。

## 背景

ReelShort 已经具备 Docker Compose 单机部署、PostgreSQL、Redis、Nginx、Spring Boot、Flask 内容源和 Flyway 数据库迁移。当前仍缺少长期运行必须具备的数据保护边界：数据库备份、配置备份、备份保留、恢复步骤和演练清单。

本阶段目标是在不引入复杂运维平台的前提下，为单机部署提供可执行的备份/恢复基础脚本和文档。当前本机未安装 Docker，因此本阶段做脚本静态校验和项目级回归，不执行真实容器备份。

## 方案选择

### 方案 A：只写文档，人工执行 `pg_dump`

改动最小，但容易在故障时遗漏环境变量、文件路径和恢复顺序，不适合作为长期运维边界。

### 方案 B：提供 PowerShell 脚本 + 文档

适合当时 Windows 开发环境和单机部署形态。历史方案曾设想通过 `docker compose exec postgres pg_dump` 导出 PostgreSQL 并复制 `.env`；该明文配置复制已被后续安全加固取代。当前脚本默认排除 `.env`，显式配置灾备必须使用 Windows DPAPI `CurrentUser` 加密，并按保留天数清理旧备份。恢复脚本通过 `pg_restore --clean --if-exists` 恢复数据库，并明确需要先停止后端避免写入。

### 方案 C：引入外部备份系统

例如 restic、对象存储、定时调度平台。长期可以接入，但当前阶段会引入额外部署复杂度。

推荐采用方案 B。

## 备份对象

必须备份：

- PostgreSQL 业务数据库：用户、内容缓存、观看记录、积分、订单、支付事件、后台审计等核心数据。
- 部署配置快照（不含明文 `infra/.env`）；如确需配置灾备，使用 Windows DPAPI `CurrentUser` 加密文件。
- `infra/docker-compose.yml`：部署拓扑。
- `backend/src/main/resources/db/migration`：Flyway 迁移脚本快照。
- `docs/deploy/README.md` 和 `docs/deploy/backup-restore.md`：部署与恢复说明快照。

不备份 Redis 作为核心数据。Redis 只作为缓存、限流和短期状态，重启丢失不应影响核心数据正确性。

## 目录结构

备份默认输出到 `infra/backups`，该目录必须被 Git 忽略。

单次备份目录：

```text
infra/backups/20260627-163000/
  database.dump
  manifest.json
  config/
    environment.dpapi (仅显式 Windows 加密配置备份)
    docker-compose.yml
    deploy-README.md
    backup-restore.md
    db/migration/*.sql
```

`manifest.json` 记录备份时间、数据库名、Compose 项目目录、生成命令、文件列表和恢复提示。

## 脚本设计

新增脚本：

- `infra/scripts/backup.ps1`
- `infra/scripts/restore.ps1`
- `infra/scripts/verify-backup-scripts.ps1`

`backup.ps1`：

- 参数：`-EnvFile`、`-OutputRoot`、`-RetentionDays`
- 校验 `.env` 存在
- 通过 `docker compose --env-file <env> -f infra/docker-compose.yml exec -T postgres pg_dump -Fc` 在容器内生成 dump，并用 `docker compose cp` 复制到宿主机
- 复制非秘密配置快照；仅在显式 `-IncludeEncryptedConfig` 且 Windows DPAPI 可用时生成加密环境配置
- 写入 `manifest.json`
- 清理超过保留天数的旧备份目录

`restore.ps1`：

- 参数：`-BackupDir`、`-EnvFile`
- 校验 `database.dump` 和 manifest 存在
- 要求用户显式传入 `-ConfirmRestore` 才执行破坏性恢复
- 提示先停止 backend/nginx
- 通过 `docker compose --env-file <env> -f infra/docker-compose.yml cp` 将 dump 复制到容器，再执行 `pg_restore --clean --if-exists --no-owner --dbname=<db>` 恢复

`verify-backup-scripts.ps1`：

- 不调用 Docker
- 校验脚本文件存在
- 校验备份目录已被 Git 忽略
- 校验脚本包含关键命令和安全参数

## 测试策略

当前机器未安装 Docker，因此不执行真实备份恢复。验证分两层：

- 静态脚本验证：`infra/scripts/verify-backup-scripts.ps1`
- 项目回归：后端测试、Android core 测试、Flask pytest、后台 build

后续部署到有 Docker 的机器后，应按 `docs/deploy/backup-restore.md` 执行一次恢复演练。
