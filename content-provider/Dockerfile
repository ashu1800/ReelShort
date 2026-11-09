FROM python:3.13-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# P2-16/P3-22：生产改由 gunicorn 承载（Werkzeug 开发服务器不再用于生产）。
# CONTENT_PROVIDER_HOST/PORT 继续沿用原语义，作为 gunicorn 的绑定地址与端口。
ENV CONTENT_PROVIDER_HOST=0.0.0.0
ENV CONTENT_PROVIDER_PORT=5000

EXPOSE 5000

# 后台下发的上游账号要落盘（0600）以便内容源重启后自恢复；容器以 nobody 运行，
# 所以先在镜像里建好属主正确的可写目录（compose 会把它挂成卷）。
RUN install -d -o nobody -g nogroup /app/data
ENV REELSHORT_ACCOUNT_FILE=/app/data/.account.json

# 非 root 运行；内部共享密钥 CONTENT_PROVIDER_INTERNAL_TOKEN 必须由部署环境注入，
# 未配置时除 /health 外的所有端点都会被拒绝（fail-closed）。
USER nobody

# 单 worker + 多线程：上游登录态（session/uid）只存在进程内存里，多 worker 会各存一份，
# 后台下发账号只会命中其中一个，另一个永远读不到新账号（见 app.py 的 ReelShortClient）。
CMD ["sh", "-c", "gunicorn -w 1 --threads 16 -t 30 -b ${CONTENT_PROVIDER_HOST}:${CONTENT_PROVIDER_PORT} --access-logfile - --error-logfile - 'app:create_app()'"]
