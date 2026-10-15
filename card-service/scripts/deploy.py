#!/usr/bin/env python3
"""卡密服务部署脚本（paramiko，SSH 直连，不需要服务器上有 Go/Node）。

做三件事，且可重复执行（幂等）：
  1. 上传二进制到 /srv/cardservice/releases/cardservice-<版本> 并切换 current 符号链接
  2. 安装 systemd 服务（含每日备份定时器）并启动/重启
  3. 安装 Nginx vhost + 安全头片段，必要时用 certbot 签发证书

示例：
  python scripts/deploy.py --host 104.143.38.190 --user root --version 0.1.0
  python scripts/deploy.py --host 1.2.3.4 --skip-cert --skip-nginx     # 只更新二进制
密码来源：--password、环境变量 CARDSERVICE_DEPLOY_PASSWORD，或交互式输入。
"""

from __future__ import annotations

import argparse
import getpass
import os
import posixpath
import secrets
import sys
import time
from pathlib import Path

# Windows 控制台默认可能是 GBK，避免中文/符号输出触发编码错误（曾导致摘要打印中断）。
for stream in (sys.stdout, sys.stderr):
    try:
        stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

try:
    import paramiko
except ImportError:  # pragma: no cover
    print("缺少 paramiko：pip install paramiko", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).resolve().parent.parent
SERVICE_USER = "cardservice"
APP_DIR = "/srv/cardservice"
DB_DIR = "/var/lib/cardservice"
LOG_DIR = "/var/log/cardservice"
BACKUP_DIR = "/var/backups/cardservice"
ETC_DIR = "/etc/cardservice"
ENV_FILE = posixpath.join(ETC_DIR, "cardservice.env")
PORT = 8090


def log(msg: str) -> None:
    print(msg, flush=True)


class Remote:
    def __init__(self, host: str, user: str, password: str, port: int = 22) -> None:
        self.client = paramiko.SSHClient()
        self.client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self.client.connect(host, port=port, username=user, password=password,
                            timeout=20, banner_timeout=30, auth_timeout=30,
                            allow_agent=False, look_for_keys=False)
        self.sftp = self.client.open_sftp()

    def close(self) -> None:
        try:
            self.sftp.close()
        finally:
            self.client.close()

    def run(self, cmd: str, check: bool = True, quiet: bool = False) -> tuple[int, str, str]:
        _stdin, stdout, stderr = self.client.exec_command(cmd, timeout=180)
        out = stdout.read().decode("utf-8", "replace")
        err = stderr.read().decode("utf-8", "replace")
        rc = stdout.channel.recv_exit_status()
        if not quiet:
            log(f"  $ {cmd}")
            if out.strip():
                log("    " + out.strip().replace("\n", "\n    "))
            if err.strip():
                log("    ! " + err.strip().replace("\n", "\n    "))
        if check and rc != 0:
            raise RuntimeError(f"远程命令失败(rc={rc}): {cmd}\n{err}")
        return rc, out, err

    def put_text(self, remote: str, content: str, mode: int = 0o644) -> None:
        with self.sftp.file(remote, "w") as fh:
            fh.write(content)
        self.sftp.chmod(remote, mode)

    def put_file(self, local: Path, remote: str, mode: int = 0o755) -> None:
        total = local.stat().st_size
        last = [0.0]

        def progress(done: int, _total: int) -> None:
            now = time.time()
            if now - last[0] > 2 or done == total:
                last[0] = now
                log(f"    上传 {done * 100 // max(total, 1)}%  ({done // 1024} KiB / {total // 1024} KiB)")

        self.sftp.put(str(local), remote, callback=progress)
        self.sftp.chmod(remote, mode)

    def exists(self, remote: str) -> bool:
        try:
            self.sftp.stat(remote)
            return True
        except OSError:
            return False

    def read_text(self, remote: str) -> str:
        with self.sftp.file(remote, "r") as fh:
            return fh.read().decode("utf-8", "replace")


def parse_env_file(content: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in content.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def new_password() -> str:
    alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%^*-_=+"
    return "".join(secrets.choice(alphabet) for _ in range(20))


def render_env_file(admin_user: str, admin_password: str) -> str:
    return f"""# /etc/cardservice/cardservice.env（由 scripts/deploy.py 生成，chmod 600 属主 root）
CARDSERVICE_ADDR=127.0.0.1:{PORT}
CARDSERVICE_DB_PATH={DB_DIR}/cardservice.db

# 首次启动创建初始管理员；创建完成后删除下面两行即可，
# 需要重置口令时执行：{APP_DIR}/current/cardservice -reset-admin-password {admin_user}
CARDSERVICE_BOOTSTRAP_ADMIN_USERNAME={admin_user}
CARDSERVICE_BOOTSTRAP_ADMIN_PASSWORD={admin_password}
CARDSERVICE_BOOTSTRAP_ADMIN_DISPLAY=超级管理员

CARDSERVICE_COOKIE_SECURE=true
CARDSERVICE_TRUSTED_PROXIES=127.0.0.1/32,::1/128
CARDSERVICE_LOG_LEVEL=info
CARDSERVICE_ACCESS_LOG=true
CARDSERVICE_RATE_LIMIT_GLOBAL_PER_MINUTE=600
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="部署卡密服务")
    parser.add_argument("--host", required=True, help="服务器 IP 或域名")
    parser.add_argument("--user", default="root", help="SSH 账号（默认 root）")
    parser.add_argument("--port", type=int, default=22, help="SSH 端口")
    parser.add_argument("--password", default=os.environ.get("CARDSERVICE_DEPLOY_PASSWORD", ""),
                        help="SSH 密码（默认读环境变量 CARDSERVICE_DEPLOY_PASSWORD，或交互输入）")
    parser.add_argument("--binary", default=str(ROOT / "dist" / "cardservice-linux-amd64"),
                        help="linux/amd64 二进制路径")
    parser.add_argument("--version", default="", help="版本号（默认用构建时间戳）")
    parser.add_argument("--domain", default="card.hjj888.cc", help="对外域名（Nginx server_name）")
    parser.add_argument("--admin-user", default="admin", help="初始管理员账号")
    parser.add_argument("--email", default="", help="certbot 注册邮箱（已有账号时可不填）")
    parser.add_argument("--skip-nginx", action="store_true", help="不安装/更新 Nginx 配置")
    parser.add_argument("--skip-cert", action="store_true", help="不签发证书")
    parser.add_argument("--skip-restart", action="store_true", help="只上传与安装，不重启服务")
    parser.add_argument("--force-env", action="store_true", help="覆盖已有 /etc/cardservice/cardservice.env")
    args = parser.parse_args()

    binary = Path(args.binary)
    if not binary.is_file():
        log(f"找不到二进制 {binary}，请先运行：powershell -File scripts/build.ps1")
        return 1
    version = args.version or time.strftime("%Y%m%d-%H%M%S")
    password = args.password or getpass.getpass(f"{args.user}@{args.host} 的 SSH 密码: ")

    log(f"== 连接 {args.user}@{args.host}:{args.port} ==")
    remote = Remote(args.host, args.user, password, args.port)
    try:
        _rc, os_release, _ = remote.run("lsb_release -ds 2>/dev/null || cat /etc/os-release | head -1", quiet=True)
        _rc, arch, _ = remote.run("uname -m", quiet=True)
        log(f"  目标机：{os_release.strip()} / {arch.strip()}")

        log("== 1/7 准备系统用户与目录 ==")
        remote.run(
            f"id -u {SERVICE_USER} >/dev/null 2>&1 || "
            f"useradd --system --home-dir {APP_DIR} --shell /usr/sbin/nologin {SERVICE_USER}",
            quiet=True,
        )
        remote.run(
            f"install -d -o {SERVICE_USER} -g {SERVICE_USER} -m 750 {DB_DIR} {LOG_DIR} {BACKUP_DIR} && "
            f"install -d -o root -g root -m 755 {APP_DIR} {APP_DIR}/releases {ETC_DIR}",
            quiet=True,
        )
        remote.run(f"install -d -o root -g root -m 755 {APP_DIR}/releases/cardservice-{version}", quiet=True)
        log(f"  目录就绪：{APP_DIR} / {DB_DIR} / {LOG_DIR} / {BACKUP_DIR}")

        log(f"== 2/7 上传二进制（{binary.stat().st_size // 1024} KiB）==")
        remote_bin = f"{APP_DIR}/releases/cardservice-{version}/cardservice"
        remote.put_file(binary, remote_bin, 0o755)
        remote.run(f"ln -sfn releases/cardservice-{version} {APP_DIR}/current && ls -l {APP_DIR}/current",
                   quiet=True)

        log("== 3/7 环境文件 /etc/cardservice/cardservice.env ==")
        generated_password = ""
        if remote.exists(ENV_FILE) and not args.force_env:
            existing = parse_env_file(remote.read_text(ENV_FILE))
            log(f"  已存在，保留现有配置（DB={existing.get('CARDSERVICE_DB_PATH', '?')}，"
                f"admin={existing.get('CARDSERVICE_BOOTSTRAP_ADMIN_USERNAME', '已移除')}）")
        else:
            generated_password = new_password()
            remote.put_text(ENV_FILE, render_env_file(args.admin_user, generated_password), 0o600)
            remote.run(f"chown root:root {ENV_FILE} && chmod 600 {ENV_FILE}", quiet=True)
            log(f"  已写入（初始管理员 {args.admin_user}，口令见最后输出）")

        log("== 4/7 systemd 服务 ==")
        remote.put_text("/etc/systemd/system/cardservice.service",
                        (ROOT / "deploy" / "cardservice.service").read_text(encoding="utf-8"), 0o644)
        remote.put_text("/etc/systemd/system/cardservice-backup.service",
                        (ROOT / "deploy" / "cardservice-backup.service").read_text(encoding="utf-8"), 0o644)
        remote.put_text("/etc/systemd/system/cardservice-backup.timer",
                        (ROOT / "deploy" / "cardservice-backup.timer").read_text(encoding="utf-8"), 0o644)
        remote.run("systemctl daemon-reload", quiet=True)
        remote.run("systemctl enable cardservice.service >/dev/null 2>&1", quiet=True)
        remote.run("systemctl enable --now cardservice-backup.timer >/dev/null 2>&1", quiet=True)
        if args.skip_restart:
            log("  已安装（按要求跳过重启）")
        else:
            remote.run("systemctl restart cardservice.service")
            time.sleep(2)
            remote.run("systemctl is-active cardservice.service && systemctl --no-pager --lines=0 status cardservice.service | head -6",
                       check=False)

        log("== 5/7 本机健康检查 ==")
        rc, out, _ = remote.run(f"curl -s -m 5 http://127.0.0.1:{PORT}/healthz", check=False, quiet=True)
        if rc != 0 or '"status":"ok"' not in out:
            remote.run(f"tail -n 40 {LOG_DIR}/cardservice-stderr.log", check=False)
            raise RuntimeError(f"服务未通过健康检查：{out}")
        log(f"  {out.strip()}")

        if not args.skip_nginx:
            log("== 6/7 Nginx vhost ==")
            remote.put_text("/etc/nginx/snippets/cardservice-headers.conf",
                            (ROOT / "deploy" / "cardservice-headers.conf").read_text(encoding="utf-8"), 0o644)
            vhost = (ROOT / "deploy" / "nginx-card.conf").read_text(encoding="utf-8")
            remote.put_text("/etc/nginx/sites-available/card", vhost, 0o644)
            remote.run("ln -sfn /etc/nginx/sites-available/card /etc/nginx/sites-enabled/card", quiet=True)
            remote.run("nginx -t && systemctl reload nginx")

        if not args.skip_cert:
            log("== 7/7 Let's Encrypt 证书 ==")
            rc, out, _ = remote.run(
                f"certbot certificates 2>/dev/null | grep -c 'Certificate Name: {args.domain}'",
                check=False, quiet=True)
            if out.strip().endswith("1"):
                log(f"  {args.domain} 证书已存在，跳过签发")
            else:
                email_arg = f"-m {args.email} " if args.email else ""
                rc, _out, _err = remote.run(
                    f"certbot --nginx -d {args.domain} --non-interactive --agree-tos "
                    f"{email_arg}--redirect", check=False)
                if rc != 0 and not args.email:
                    # 服务器上已有"无邮箱"注册的 ACME 账号时，需要显式声明不带邮箱。
                    log("  首次尝试失败，改用 --register-unsafely-without-email 重试")
                    remote.run(f"certbot --nginx -d {args.domain} --non-interactive --agree-tos "
                               f"--register-unsafely-without-email --redirect", check=False)
                rc, out, _ = remote.run(f"test -f /etc/letsencrypt/live/{args.domain}/fullchain.pem && echo OK",
                                        check=False, quiet=True)
                if "OK" not in out:
                    log("  [WARN] 证书未签发成功，请检查 Cloudflare 是否强制 HTTPS 跳转或 DNS 是否指向本机")

        log("== 验收 ==")
        remote.run(f"curl -s -o /dev/null -w 'origin http=%{{http_code}}\\n' -H 'Host: {args.domain}' "
                   f"http://127.0.0.1/", check=False)
        remote.run(f"curl -s -m 15 -o /dev/null -w 'public https=%{{http_code}}\\n' https://{args.domain}/",
                   check=False)
        remote.run("systemctl --no-pager --lines=0 status cardservice.service | head -5", check=False)

        log("")
        log("部署完成 [OK]")
        log(f"  版本      : {version}")
        log(f"  服务      : systemctl status cardservice  （{SERVICE_USER} 用户，监听 127.0.0.1:{PORT}）")
        log(f"  数据库    : {DB_DIR}/cardservice.db（每日 04:10 自动备份到 {BACKUP_DIR}）")
        log(f"  后台地址  : https://{args.domain}/")
        if generated_password:
            log(f"  管理员    : {args.admin_user} / {generated_password}")
            log("              （首次登录会强制改密；改完请从环境文件删除 BOOTSTRAP 两行并重启）")
        else:
            log(f"  管理员    : 沿用已有配置（重置口令：{APP_DIR}/current/cardservice -reset-admin-password <账号>）")
        log(f"  自检      : powershell -File scripts/prod-check.ps1 -AdminUser {args.admin_user} -AdminPassword '***'")
        return 0
    finally:
        remote.close()


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # noqa: BLE001
        print(f"\n部署失败：{exc}", file=sys.stderr)
        sys.exit(1)
