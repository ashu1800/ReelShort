# ReelShort systemd 原生部署（无 Docker）

本页记录在**已有 Ubuntu 24.04 单机**上用 systemd 直接运行 ReelShort 的完整步骤，作为 `docs/deploy/README.md`（Docker Compose）之外的第二条部署路径。两条路径的**运行形态一致**：宿主机 Nginx 是唯一公网入口，Spring Boot、PostgreSQL、Redis、Flask 内容源都只监听本机。

适用场景：目标机器磁盘/内存有限、到 Docker Hub 的国际带宽很差（实测约 100 KB/s，拉 `gradle:8.13-jdk17` 需要数小时），或运维规范要求不使用容器。

## 0. 组件与端口

| 组件 | 运行方式 | 监听 |
|---|---|---|
| Nginx | 宿主机 apt 包 | 80 / 443（公网入口，TLS 终止） |
| backend | systemd `reelshort-backend.service` | 127.0.0.1:8080 |
| content-provider | systemd `reelshort-content-provider.service`（gunicorn） | 127.0.0.1:5000 |
| PostgreSQL | systemd `postgresql`（apt，16.x） | 127.0.0.1:5432 |
| Redis | systemd `redis-server`（apt） | 127.0.0.1:6379 |
| admin-web | 静态文件由宿主机 Nginx 直接托管 | `/srv/reelshort/admin-web/dist` |

## 1. 依赖安装

```bash
apt-get update
apt-get install -y openjdk-17-jre-headless postgresql postgresql-contrib \
                   nginx certbot python3-certbot-nginx python3-venv python3-pip
```

2 GB 内存的机器建议先加 2 GB swap（构建/首个 JVM 启动更稳）：

```bash
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

## 2. 代码与产物

```bash
install -d /srv/reelshort
tar -xzf reelshort-deploy.tar.gz -C /srv/reelshort      # 仓库源码（含 infra/.env.example）
```

后端 jar 与前端静态产物**在本地（或 CI）构建后上传**，避免在生产机上跑 Gradle/npm：

```bash
# 本地
backend> gradlew.bat bootJar -x test            # 产出 backend/build/libs/backend-0.0.1-SNAPSHOT.jar
admin-web> npm ci && npm run build              # 产出 admin-web/dist
# 上传后
install -m 644 app.jar /srv/reelshort/backend/app.jar
install -d /srv/reelshort/admin-web/dist
cp -r admin-web-dist/. /srv/reelshort/admin-web/dist/
```

> 只用一台机器时也可以直接在生产机装 JDK + Gradle 构建，但需要额外下载 Gradle 发行包与依赖，慢链路下不如本地构建上传。

## 3. 运行账号与日志

```bash
useradd --system --home-dir /srv/reelshort --shell /usr/sbin/nologin reelshort
install -d -o reelshort -g reelshort -m 750 /var/log/reelshort
install -d -o root -g root -m 755 /etc/reelshort
```

## 4. PostgreSQL

```bash
PG_PASS="$(openssl rand -hex 24)"          # 与 /etc/reelshort/backend.env 中的 REELSHORT_DB_PASSWORD 保持一致
systemctl enable --now postgresql
sudo -u postgres psql -c "CREATE ROLE reelshort LOGIN PASSWORD '$PG_PASS'"
sudo -u postgres createdb -O reelshort reelshort
```

schema 由后端启动时的 Flyway 自动迁移（当前最新 `V31__review_remediation.sql`），随后 JPA `ddl-auto=validate` 校验结构。

## 5. 后端环境文件 `/etc/reelshort/backend.env`

复用 `infra/.env.example` 的键名（`chmod 600`，属主 root）。关键项：

```ini
REELSHORT_DB_URL=jdbc:postgresql://127.0.0.1:5432/reelshort
REELSHORT_DB_USERNAME=reelshort
REELSHORT_DB_PASSWORD=<第 4 步的 PG_PASS>
REELSHORT_DB_POOL_MAXIMUM_SIZE=15
REELSHORT_TRUSTED_PROXIES=127.0.0.1/32        # 宿主机 Nginx 走 loopback 回源
REELSHORT_REDIS_HOST=127.0.0.1
REELSHORT_REDIS_PORT=6379
REELSHORT_RATE_LIMIT_STORE=redis
REELSHORT_CONTENT_PROVIDER_BASE_URL=http://127.0.0.1:5000
CONTENT_PROVIDER_INTERNAL_TOKEN=<与内容源一致的共享密钥>
REELSHORT_LOG_FILE=/var/log/reelshort/backend.log
REELSHORT_SYSTEM_LOG_ROOT=/var/log/reelshort
REELSHORT_ADMIN_USERNAME=admin
REELSHORT_ADMIN_PASSWORD_HASH=<bcrypt>        # htpasswd -bnBC 12 '' 'pass' | tr -d ':\n' | sed 's/^\$2y\$/\$2a$/'
REELSHORT_PAYMENT_CALLBACK_SECRET=<随机>
REELSHORT_INTERNAL_SUPER_TOKEN=<随机>
REELSHORT_SITE_URL=https://shortlink.hjj888.cc
REELSHORT_COS_SECRET_ID / REELSHORT_COS_SECRET_KEY / REELSHORT_COS_REGION / REELSHORT_COS_BUCKET
SERVER_ADDRESS=127.0.0.1                      # 必须：Tomcat 默认监听 0.0.0.0，会绕过 Nginx 直接暴露公网
SPRING_HTTP_CLIENT_READ_TIMEOUT=60s           # 内容源冷缓存单次 4-15s，默认 5s 会导致内容接口 503
```

## 6. 内容源环境文件 `/etc/reelshort/content-provider.env`

```ini
CONTENT_PROVIDER_INTERNAL_TOKEN=<与后端一致>
CONTENT_PROVIDER_HOST=127.0.0.1
CONTENT_PROVIDER_PORT=5000
REELSHORT_UPSTREAM_SITE_URL=https://www.reelshort.com   # 注意：不是本平台域名！
REELSHORT_SITE_ID=37
REELSHORT_REQUEST_TIMEOUT_SECONDS=10
REELSHORT_MAX_RESPONSE_BYTES=5242880
REELSHORT_MAX_REDIRECTS=3
```

> **易错点**：`REELSHORT_SITE_URL` 在后端表示"本平台公网域名"，在内容源表示"上游 ReelShort 站点"。把后者误设为本平台域名后，内容源会去抓自己，拿不到 Next.js `buildId`，所有内容接口返回 502（`upstream build id not found`）。因此内容源请只使用 `REELSHORT_UPSTREAM_SITE_URL`。

```bash
cd /srv/reelshort/content-provider
python3 -m venv .venv && ./.venv/bin/pip install -r requirements.txt
```

## 7. systemd 单元

`/etc/systemd/system/reelshort-backend.service`

```ini
[Unit]
Description=ReelShort backend (Spring Boot)
After=network-online.target postgresql.service redis-server.service
Wants=network-online.target
Requires=postgresql.service

[Service]
Type=simple
User=reelshort
Group=reelshort
WorkingDirectory=/srv/reelshort/backend
EnvironmentFile=/etc/reelshort/backend.env
# 运行展开目录 + 版本化 classes（薄更新，见第 13 节），首次部署见第 2 节说明
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=38 -XX:+ExitOnOutOfMemoryError -cp /srv/reelshort/backend/classes:/srv/reelshort/backend/app/BOOT-INF/lib/* com.reelshort.backend.BackendApplication
SuccessExitStatus=143
Restart=always
RestartSec=5
TimeoutStopSec=45
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ReadWritePaths=/var/log/reelshort
LimitNOFILE=65535
StandardOutput=append:/var/log/reelshort/backend-stdout.log
StandardError=append:/var/log/reelshort/backend-stderr.log

[Install]
WantedBy=multi-user.target
```

`/etc/systemd/system/reelshort-content-provider.service`

```ini
[Unit]
Description=ReelShort content provider (Flask + gunicorn)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=reelshort
Group=reelshort
WorkingDirectory=/srv/reelshort/content-provider
EnvironmentFile=/etc/reelshort/content-provider.env
ExecStart=/srv/reelshort/content-provider/.venv/bin/gunicorn -w 2 --threads 8 -t 30 \
  -b 127.0.0.1:5000 --access-logfile - --error-logfile - app:create_app()
Restart=always
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
LimitNOFILE=65535
StandardOutput=append:/var/log/reelshort/content-provider.log
StandardError=append:/var/log/reelshort/content-provider.err.log

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now reelshort-content-provider reelshort-backend
```

## 8. 宿主机 Nginx（公网入口）

`/etc/nginx/snippets/reelshort-headers.conf`（与 `admin-web/nginx.conf` 的安全头保持一致，供多个 location 复用）

```nginx
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'" always;
```

`/etc/nginx/sites-available/reelshort`

```nginx
server {
    listen 80;
    listen [::]:80;
    server_name shortlink.hjj888.cc;

    root /srv/reelshort/admin-web/dist;
    index index.html;
    client_max_body_size 20m;

    # 域名走 Cloudflare 时：用 CF-Connecting-IP 还原真实客户端 IP，供后端限流按客户端计数
    # set_real_ip_from <Cloudflare IPv4/IPv6 段>;   # https://www.cloudflare.com/ips-v4 / ips-v6
    real_ip_header CF-Connecting-IP;
    real_ip_recursive on;

    include snippets/reelshort-headers.conf;

    location = /downloads/android { return 404; }
    location ^~ /downloads/android/ { return 404; }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;   # 只透传真实客户端 IP，避免伪造限流键
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 150s;
        proxy_send_timeout 150s;
    }

    location = /index.html {
        include snippets/reelshort-headers.conf;
        add_header Cache-Control "no-store" always;
    }

    location ^~ /assets/ {
        include snippets/reelshort-headers.conf;
        expires 1y;
        add_header Cache-Control "public, immutable" always;
    }

    location / { try_files $uri $uri/ /index.html; }
}
```

`/actuator` 不对外暴露；`backend` 必须同时用 `SERVER_ADDRESS=127.0.0.1` 绑定 loopback，否则 8080 会被公网直接扫描到（实测扫描器会直接请求 `/.env`、`/.git/HEAD`）。

```bash
ln -sf /etc/nginx/sites-available/reelshort /etc/nginx/sites-enabled/reelshort
nginx -t && systemctl enable --now nginx
```

## 9. HTTPS

```bash
certbot --nginx -d shortlink.hjj888.cc --non-interactive --agree-tos --register-unsafely-without-email --redirect
```

- 该域名在 Cloudflare 后面：Cloudflare 回源到本机 443，源站证书由 Certbot 管理；HTTP-01 校验通过 Cloudflare 回源的 80 端口完成（要求 Cloudflare 未开启"Always Use HTTPS"）。
- Cloudflare SSL/TLS 模式建议 **Full (strict)**；若为 Flexible，边缘到源站为明文 HTTP，`--redirect` 会造成重定向环路。
- 续期：`certbot renew` 已由 `certbot.timer` 接管，可用 `certbot renew --dry-run` 验证。

## 10. 日志轮转与备份

`/etc/logrotate.d/reelshort`

```
/var/log/reelshort/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
    su reelshort reelshort
}
```

`/etc/cron.d/reelshort-backup`（PostgreSQL 自定义格式备份，可直接 `pg_restore`）

```
30 3 * * * postgres /usr/bin/pg_dump -Fc -d reelshort -f /var/backups/reelshort/reelshort-$(date +\%Y\%m\%d).dump >> /var/log/reelshort/backup.log 2>&1
```

## 11. 验收

```bash
systemctl is-active reelshort-backend reelshort-content-provider nginx postgresql redis-server
curl -sS http://127.0.0.1:8080/actuator/health                 # {"status":"UP"}
curl -sS http://127.0.0.1:5000/health                          # {"status":"UP"}
curl -sS -o /dev/null -w '%{http_code}\n' https://<域名>/       # 200（后台 SPA）
curl -sS https://<域名>/api/app/auth/captcha | head -c 80       # 游客可访问的验证码
curl -sS -X POST https://<域名>/api/admin/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<密码>"}'                 # 200 + token
```

首次部署后还需要人工完成的两件事：

1. **后台系统配置**里填写 `vip.collection-address`（TRC20 收款地址），否则 App 的 VIP 付费弹层没有可扫地址。
2. 确认 `content_refresh` 首次货架刷新成功：`GET /api/admin/content/cache` 应显示货架 `HEALTHY`；空库首次刷新耗时约 10-30 秒，属正常。

## 12. 与 Docker Compose 路径的差异

- 无容器隔离与 `healthcheck` 自动重启语义，改由 systemd `Restart=always` 承担。
- `REELSHORT_TRUSTED_PROXIES` 从 Docker 子网改为 `127.0.0.1/32`。
- `admin-web` 静态文件由宿主机 Nginx 直接托管，不再有内部 nginx 容器；`/api/` 直接反代到 `127.0.0.1:8080`。
- `CONTENT_PROVIDER_HOST` 固定 `127.0.0.1`（不对外开放），因此内容源除 `/health` 外仍要求 `X-Content-Provider-Token`。

## 13. 日常更新：只上传真正变化的部分（薄更新）

首次部署要传 133 MB 的 fat jar，但**这个 jar 里 99.6% 是永不变化的第三方依赖**（实测：`BOOT-INF/lib` 138.6 MB / 219 个 jar，`BOOT-INF/classes` 仅 1.39 MB）。因此生产后端按"展开目录 + 版本化 classes"运行，日常更新只需替换 classes：

```
/srv/reelshort/backend/
├── app/                       # 首次由 app.jar 展开（zipfile 解开即可，无需 unzip）
│   └── BOOT-INF/lib/          # 第三方依赖 138 MB —— 只有升级依赖版本才需要动
├── app.jar                    # 保留完整 jar，用于彻底回滚
├── classes -> releases/classes-<版本>/classes     # 应用代码（符号链接，当前生效版本）
└── releases/
    ├── classes-initial-<日期>/                     # 首次部署时的代码，回滚点
    └── classes-<版本>/classes/                     # 每次更新新增一个版本目录
```

准备上传物（本地，自动构建 + 只提取 classes + 打印上传指令）：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/stage-backend-update.ps1 -Version 20261013-1
```

切换生效（服务器）：

```bash
# 1) 上传到 /srv/reelshort/backend/releases/classes-20261013-1（SFTP 目录上传）
chown -R reelshort:reelshort /srv/reelshort/backend/releases/classes-20261013-1
ln -sfn /srv/reelshort/backend/releases/classes-20261013-1/classes /srv/reelshort/backend/classes
systemctl restart reelshort-backend
curl -sS http://127.0.0.1:8080/actuator/health        # {"status":"UP"}
```

各改动类型的实际上传量（链路实测约 90-230 KB/s）：

| 改动 | 上传内容 | 大小 | 是否重启 |
|---|---|---|---|
| 后端 Java/配置/Flyway 迁移 | `releases/classes-<版本>` + 重指符号链接 | **约 1.4 MB** | 重启 backend |
| 后台前端 | `admin-web/dist` | **约 1.4 MB** | 不需要 |
| 内容源 Python | `content-provider/app.py` | **约 42 KB** | 重启 content-provider |
| systemd / Nginx / 环境文件 | 对应配置文件 | KB 级 | 按需 reload/restart |
| 依赖版本变更（少见） | `app/BOOT-INF/lib` 中变化的 jar（或重传 app.jar 后重新展开） | 几 MB 起 | 重启 backend |

回滚：

```bash
# 代码回滚（秒级，无需重新上传）
ln -sfn /srv/reelshort/backend/releases/classes-initial-20261012/classes /srv/reelshort/backend/classes
systemctl restart reelshort-backend
# 彻底回滚到 fat jar 运行方式
# 把 ExecStart 换回：-jar /srv/reelshort/backend/app.jar
```

注意：

- 版本化目录是**新目录**，天然不存在"已删除的旧 class 残留"问题；旧的 `releases/classes-*` 可以定期人工清理（每个约 2.4 MB）。
- `bootJar` 会同时产出 `backend-0.0.1-SNAPSHOT-plain.jar`（瘦包，没有 `BOOT-INF`），提取脚本必须使用可执行包 `backend-0.0.1-SNAPSHOT.jar`。
- Flyway 迁移随 classes 一起更新，重启即自动执行；`ddl-auto=validate` 会校验结构。
- 首次部署若直接采用本形态，可省掉 fat jar 传输之外的一切步骤：`python3 -m zipfile -e app.jar app/` 展开后再建符号链接即可（`unzip` 未安装也能工作）。
