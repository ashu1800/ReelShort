# 单机备份与恢复手册

## 适用范围

本手册用于长期单机部署环境下的 PostgreSQL 业务数据和部署配置备份恢复。当前系统把 PostgreSQL 作为核心持久化数据源，Redis 只保存缓存、限流计数和短期状态，因此 Redis 数据不纳入恢复必需项。

## 备份内容

`infra/scripts/backup.ps1` 默认生成一个时间戳目录：

```text
infra/backups/YYYYMMDD-HHMMSS/
  database.dump
  manifest.json
  config/
    .env
    docker-compose.yml
    deploy-README.md
    backup-restore.md
    db/migration/*.sql
```

备份包含：

- PostgreSQL 自定义格式 dump。
- 当前部署使用的 `infra/.env`。
- 当前 `infra/docker-compose.yml`。
- 当前部署说明和备份恢复手册快照。
- 当前 Flyway 迁移脚本快照。

`infra/backups/` 已被 Git 忽略，不能提交到仓库。

## 执行备份

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/backup.ps1
```

常用参数：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/backup.ps1 `
  -EnvFile infra/.env `
  -OutputRoot infra/backups `
  -RetentionDays 14
```

脚本通过 Docker Compose 调用 PostgreSQL 容器内的 `pg_dump -Fc`，然后将 dump 文件复制到宿主机备份目录。`RetentionDays` 大于 0 时会删除超过保留天数的旧备份目录。

## 执行恢复

恢复会清理并重建数据库对象，必须先停止会写入数据库的服务：

```powershell
docker compose --env-file infra/.env stop backend nginx
```

确认备份目录后执行：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/restore.ps1 `
  -BackupDir infra/backups/YYYYMMDD-HHMMSS `
  -ConfirmRestore
```

恢复完成后启动服务：

```powershell
docker compose --env-file infra/.env up -d backend nginx
```

`restore.ps1` 必须显式传入 `-ConfirmRestore`，否则不会执行破坏性恢复。脚本会校验 `database.dump` 和 `manifest.json` 存在，再将 dump 文件复制到 PostgreSQL 容器内执行 `pg_restore --clean --if-exists --no-owner`。

## 恢复演练清单

每次部署结构或数据库迁移发生重要变化后，应在有 Docker 的机器上执行一次恢复演练：

- 执行 `infra/scripts/backup.ps1` 并确认生成 `database.dump` 和 `manifest.json`。
- 准备一个可丢弃的 PostgreSQL 数据卷或测试环境。
- 按恢复步骤停止写入服务。
- 执行 `infra/scripts/restore.ps1 -ConfirmRestore`。
- 启动 backend 和 nginx。
- 检查 `http://localhost/actuator/health`。
- 登录后台，抽查用户、订单、积分流水、观看记录和内容缓存。
- 确认 Redis 清空后不影响核心业务数据正确性。

## 静态校验

当前开发机未安装 Docker 时，可执行静态校验：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/verify-backup-scripts.ps1
```

该校验不会调用 Docker，只验证脚本、忽略规则和关键命令契约是否存在。真实备份恢复仍需要在安装 Docker 的部署环境中演练。
