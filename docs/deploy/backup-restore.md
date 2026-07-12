# 单机备份与恢复手册

## 适用范围

本手册用于长期单机部署环境下的 PostgreSQL 业务数据和部署配置备份恢复。当前系统把 PostgreSQL 作为核心持久化数据源，Redis 只保存缓存、限流计数和短期状态，因此 Redis 数据不纳入恢复必需项。

## 备份内容

`infra/scripts/backup.ps1` 默认生成一个时间戳目录：

```text
infra/backups/YYYYMMDD-HHMMSS-fff-<random>/
  database.dump
  manifest.json
  config/
    docker-compose.yml
    deploy-README.md
    backup-restore.md
    db/migration/*.sql
```

默认备份包含：

- PostgreSQL 自定义格式 dump。
- 当前 `infra/docker-compose.yml`。
- 当前部署说明和备份恢复手册快照。
- 当前 Flyway 迁移脚本快照。

`infra/backups/` 已被 Git 忽略，不能提交到仓库。每次备份目录都包含毫秒时间和随机后缀，快速连续执行也不会复用已有目录或继承旧备份文件。

默认不会备份 `infra/.env`，避免数据库口令、超级 Token、短信供应商密钥和支付回调密钥以明文进入备份。仅在确实需要配置灾备时，才显式传入 `-IncludeEncryptedConfig`。脚本在 Windows 上使用 DPAPI `CurrentUser` 将配置直接加密为 `config/environment.dpapi`，不会创建明文临时文件；密文只能由同一台 Windows 主机上同一用户上下文解密。

备份目录会在 Windows 上移除继承 ACL，仅授予当前用户完全控制；非 Windows 上输出根和单次备份目录设置为 `0700`，`database.dump` 和 `manifest.json` 设置为 `0600`。ACL、`chmod` 或 DPAPI 加密失败时脚本会失败，不会继续生成权限过宽的备份，也不会回退为明文配置备份。非 Windows 主机仍可执行默认数据库备份，但传入 `-IncludeEncryptedConfig` 会被明确拒绝；需要跨平台配置灾备时，应使用部署平台提供的密钥管理或加密备份系统。

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

显式加密备份部署配置（仅 Windows）：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/backup.ps1 `
  -IncludeEncryptedConfig
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

数据库恢复默认不会恢复配置。仅在同一 Windows 用户上下文中，可显式解密到指定路径：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/restore.ps1 `
  -BackupDir infra/backups/YYYYMMDD-HHMMSS `
  -EnvFile infra/.env `
  -ConfirmRestore `
  -RestoreEncryptedConfig `
  -ConfigRestorePath production.env
```

`ConfigRestorePath` 只能是 `infra/restored-config/` 下的相对文件名或相对路径；绝对路径、路径穿越、逃出安全根、父目录中的 junction/symlink 等 reparse point 以及已存在的目标都会被拒绝。脚本不支持覆盖已有文件。配置目标使用 `CreateNew` 创建，并在写入明文前收紧为仅当前用户可访问；解密、创建或 ACL 设置失败时不会留下新明文文件，也不会修改旧文件。

启用配置恢复时，脚本会先完成路径校验、受限目录创建、DPAPI 解密和安全 `CreateNew` 写入，确认配置准备成功后才调用 `pg_restore`。如果数据库工具随后失败，脚本会删除本次新建的配置文件并报告失败；数据库工具自身不是跨资源事务，数据库可能已经发生部分变更，必须按数据库恢复演练和备份回滚处理。

检查恢复内容后再替换正式配置；恢复完成后必须轮换数据库密码、管理员凭据、内部超级 Token、短信供应商密钥、支付回调密钥及其他所有恢复出的秘密，然后才能重新开放服务。不得把恢复文件提交到 Git 或复制到不受控目录。

## 恢复演练清单

每次部署结构或数据库迁移发生重要变化后，应在有 Docker 的机器上执行一次恢复演练：

- 执行 `infra/scripts/backup.ps1` 并确认生成 `database.dump` 和 `manifest.json`。
- 默认备份确认不存在 `config/.env` 和 `config/environment.dpapi`；配置灾备演练必须显式使用 DPAPI 开关。
- 准备一个可丢弃的 PostgreSQL 数据卷或测试环境。
- 按恢复步骤停止写入服务。
- 执行 `infra/scripts/restore.ps1 -ConfirmRestore`。
- 启动 backend 和 nginx。
- 检查 `http://localhost/actuator/health`。
- 登录后台，抽查用户、订单、积分流水、观看记录和内容缓存。
- 确认 Redis 清空后不影响核心业务数据正确性。
- 若演练配置恢复，确认使用同一 Windows 用户可解密、其他用户不可解密，并在演练后轮换或销毁恢复出的秘密。

## 静态校验

当前开发机未安装 Docker 时，可执行静态校验：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/verify-backup-scripts.ps1
```

该校验不会调用 Docker，只验证脚本、忽略规则和关键命令契约是否存在。真实备份恢复仍需要在安装 Docker 的部署环境中演练。

静态校验同时使用隔离目录和伪 Docker 命令执行配置安全行为测试，不接触真实数据库或生产 `.env`。
