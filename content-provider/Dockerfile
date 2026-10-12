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

# 非 root 运行；内部共享密钥 CONTENT_PROVIDER_INTERNAL_TOKEN 必须由部署环境注入，
# 未配置时除 /health 外的所有端点都会被拒绝（fail-closed）。
USER nobody

CMD ["sh", "-c", "gunicorn -w 2 --threads 8 -t 30 -b ${CONTENT_PROVIDER_HOST}:${CONTENT_PROVIDER_PORT} --access-logfile - --error-logfile - 'app:create_app()'"]
