# 部署与运维

## 1. 目标环境

| 项 | 值 |
|---|---|
| 服务器 | `104.143.38.190`（Ubuntu 24.04 LTS，x86_64，2 GB 内存，与 ReelShort 现网同机） |
| 公网入口 | Nginx 1.24（宿主机 apt 包），TLS 由 Certbot/Let's Encrypt 管理 |
| DNS/CDN | `card.hjj888.cc` 由 Cloudflare 代理到本机（源站就是这台机器） |
| 服务监听 | `127.0.0.1:8090`（**不直接对公网开放**） |
| 数据 | `/var/lib/cardservice/cardservice.db`（SQLite + WAL） |
| 备份 | `/var/backups/cardservice/`（每日 04:10，保留 14 份） |
| 日志 | `/var/log/cardservice/cardservice-{stdout,stderr}.log`（journald 之外单独落盘） |

**与 ReelShort 现网的关系**：本服务只新增 `cardservice` 系统用户、`/srv/cardservice` 目录、`cardservice.service` unit、`/etc/nginx/sites-available/card` vhost 与一张 `card.hjj888.cc` 证书；**不改动** `reelshort-backend`、`reelshort-content-provider`、PostgreSQL/Redis 以及 `shortlink.hjj888.cc` 的任何配置。

## 2. 一键部署（推荐）

```powershell
# 1) 构建（前端 + linux/amd64 静态二进制；CGO 关闭，纯 Go SQLite 驱动）
powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -Version 0.1.0

# 2) 部署（幂等，可反复执行；会打印新管理员口令）
$env:CARDSERVICE_DEPLOY_PASSWORD='<SSH 密码>'
python scripts/deploy.py --host 104.143.38.190 --user root --version 0.1.0

# 只更新二进制（不动 nginx / 证书）
python scripts/deploy.py --host 104.143.38.190 --user root --version 0.1.1 --skip-nginx --skip-cert
```

脚本按 7 步执行并在每一步打印结果：系统用户与目录 → 上传二进制并切 `current` 符号链接 → 环境文件 → systemd 服务 → 本机健康检查 → Nginx vhost → 证书签发，最后打印服务状态与公网 HTTP 状态码。

## 3. 手工部署步骤（等价流程）

```bash
# 账号与目录
useradd --system --home-dir /srv/cardservice --shell /usr/sbin/nologin cardservice
install -d -o cardservice -g cardservice -m 750 /var/lib/cardservice /var/log/cardservice /var/backups/cardservice
install -d -o root -g root -m 755 /srv/cardservice/releases /etc/cardservice

# 二进制（版本化目录 + 符号链接，便于秒级回滚）
install -d /srv/cardservice/releases/cardservice-0.1.0
install -m 755 cardservice-linux-amd64 /srv/cardservice/releases/cardservice-0.1.0/cardservice
ln -sfn releases/cardservice-0.1.0 /srv/cardservice/current

# 环境文件（600 root）
install -m 600 deploy/cardservice.env.example /etc/cardservice/cardservice.env
vi /etc/cardservice/cardservice.env     # 填 CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD

# systemd
install -m 644 deploy/cardservice.service /etc/systemd/system/
install -m 644 deploy/cardservice-backup.service /etc/systemd/system/
install -m 644 deploy/cardservice-backup.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now cardservice.service cardservice-backup.timer
curl -s http://127.0.0.1:8090/healthz

# Nginx
install -m 644 deploy/cardservice-headers.conf /etc/nginx/snippets/
install -m 644 deploy/nginx-card.conf /etc/nginx/sites-available/card
ln -sfn /etc/nginx/sites-available/card /etc/nginx/sites-enabled/card
nginx -t && systemctl reload nginx

# 证书（HTTP-01；Cloudflare 会把 /.well-known/acme-challenge/ 透传到源站）
certbot --nginx -d card.hjj888.cc --non-interactive --agree-tos --redirect
```

## 4. Cloudflare 相关注意事项

1. **526 错误的成因与修复**：部署前 `https://card.hjj888.cc/` 会返回 **526 Invalid SSL certificate**——因为 Cloudflare 以 Full (strict) 回源到本机，而源站 443 只挂了 `shortlink.hjj888.cc` 的证书。签发 `card.hjj888.cc` 证书后 526 自动消失（`curl -I https://card.hjj888.cc/` 应为 200）。
2. **真实客户端 IP**：vhost 里配置了 Cloudflare 全部网段的 `set_real_ip_from` + `real_ip_header CF-Connecting-IP`，nginx 再用 `proxy_set_header X-Forwarded-For $remote_addr`（**覆盖**而不是追加）传给后端；后端只采信来自 `127.0.0.1` 的 XFF。因此通过 CF 访问时日志与限流拿到的是真实用户 IP，直连源站伪造头部无效。
3. **ACME 校验**：证书签发依赖 `http://card.hjj888.cc/.well-known/acme-challenge/...` 能到达本机。Cloudflare 的「Always Use HTTPS」如果开启且源站证书无效，校验会被重定向到 HTTPS 而失败；此时先在 CF 关闭该开关（或把记录临时改成 DNS only）签发证书，之后再打开。
4. **Bot Fight Mode / WAF**：若客户端 POST 被 CF 挑战页拦截，为该主机名配置 WAF 跳过规则，或把记录改为 DNS only（不推荐长期如此）。
5. **可选加固**：可以限制源站 443 只接受 Cloudflare 网段（`allow` + `deny`），本仓库默认不做，以免影响同一 IP 上的其他域名排障。

## 5. 备份与恢复

- 备份由 `cardservice-backup.timer` 每日 04:10 触发，执行 `cardservice -backup /var/backups/cardservice -backup-keep 14`（SQLite `VACUUM INTO`，一致性快照），保留最近 14 份。
- 手动备份：`/srv/cardservice/current/cardservice -backup /var/backups/cardservice`
- 恢复流程：
  ```bash
  systemctl stop cardservice
  cp /var/backups/cardservice/cardservice-YYYYmmdd-HHMMSS.db /var/lib/cardservice/cardservice.db
  chown cardservice:cardservice /var/lib/cardservice/cardservice.db
  rm -f /var/lib/cardservice/cardservice.db-wal /var/lib/cardservice/cardservice.db-shm
  systemctl start cardservice
  curl -s http://127.0.0.1:8090/healthz
  ```

## 6. 升级与回滚

```bash
# 升级：上传新版本目录并重指符号链接
install -d /srv/cardservice/releases/cardservice-0.1.1
install -m 755 cardservice-linux-amd64 /srv/cardservice/releases/cardservice-0.1.1/cardservice
ln -sfn releases/cardservice-0.1.1 /srv/cardservice/current
systemctl restart cardservice

# 回滚：指回旧版本并重启（秒级）
ln -sfn releases/cardservice-0.1.0 /srv/cardservice/current
systemctl restart cardservice
```

数据库迁移在启动时自动执行，且只做增量（新增表/列），回滚二进制不会破坏数据。若已执行新版本迁移，回滚后仍可正常读取旧字段。

## 7. 当前部署状态（2026-10-12 首次上线）

| 项 | 值 |
|---|---|
| 服务器 | `104.143.38.190` |
| 版本 | `0.1.1` → `/srv/cardservice/current` → `/srv/cardservice/releases/cardservice-0.1.1` |
| 服务 | `cardservice.service`：active (running)、enabled；监听 `127.0.0.1:8090` |
| 数据 | `/var/lib/cardservice/cardservice.db` |
| 备份 | `cardservice-backup.timer`：enabled + active，下一次 04:13；已演练产出 143 KB 快照 |
| Nginx | `/etc/nginx/sites-available/card` → `sites-enabled/card`（certbot 已补 443 + 强制跳转） |
| 证书 | `/etc/letsencrypt/live/card.hjj888.cc/`，有效期至 2027-01-10，自动续期 |
| 公网 | `https://card.hjj888.cc/` = 200（此前为 Cloudflare 526），`/healthz` = ok |
| 现网回归 | `shortlink.hjj888.cc` 首页与 `/api/app/release/latest` 均 200，未受影响 |

回滚到今天之前的版本：本服务此前未部署，直接 `systemctl disable --now cardservice && rm /etc/nginx/sites-enabled/card && systemctl reload nginx` 即可完全移除，不影响同机其他服务。

## 8. 故障排查

| 现象 | 排查 |
|---|---|
| `https://card.hjj888.cc/` 返回 526 | 源站证书缺失/过期：`certbot certificates`、`nginx -t`；重新 `certbot --nginx -d card.hjj888.cc` |
| 页面能打开但接口 502 | `systemctl status cardservice`、`curl -s http://127.0.0.1:8090/healthz`、`tail -n 50 /var/log/cardservice/cardservice-stderr.log` |
| 登录后台提示锁定 | 管理员登录连续失败 5 次会锁定 15 分钟（按账号+IP） |
| 客户端突然掉线 | 心跳超时（默认 180 秒）或会话被顶号；查看后台「在线会话」与「操作日志」 |
| 日志里 IP 全是 `127.0.0.1` | nginx 未设置 `set_real_ip_from` / CF 直连源站；对照第 4 节检查 |
| 数据库被锁（SQLITE_BUSY） | 服务已内置退避重试；持续出现时检查是否有外部程序长期占用该文件 |

## 9. 安全边界

- 后台 Cookie：`HttpOnly` + `Secure` + `SameSite=Strict`，写操作额外要求 `X-Requested-With: card-admin` 并校验 `Origin`。
- 初始管理员口令由部署脚本随机生成（20 位），首次登录强制修改，改密后吊销全部后台会话。
- 公共接口限流：单 IP 300 次/分钟、单卡密 60 次/分钟、单会话 120 次/分钟（后台可调）。
- 日志脱敏：卡密 `ABCD-****-****-WXYZ`、机器码保留前 6 后 4、`session_token` 从不落日志。
- 环境文件 `/etc/cardservice/cardservice.env` 权限 600、属主 root；服务以无登录权限的 `cardservice` 用户运行，systemd 开启 `NoNewPrivileges` / `ProtectSystem=full` / `ProtectHome`。
