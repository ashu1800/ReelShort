FROM gradle:8.13-jdk17 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
# curl 供容器 healthcheck 使用；显式安装 passwd 以获得 groupadd/useradd，
# 不依赖基础镜像是否预装。随后创建非 root 运行账号。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl passwd \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system reelshort \
    && useradd --system --gid reelshort --home-dir /app --shell /usr/sbin/nologin reelshort \
    && mkdir -p /app/logs \
    && chown -R reelshort:reelshort /app
COPY --from=build --chown=reelshort:reelshort /workspace/build/libs/*.jar app.jar
EXPOSE 8080
# 以非 root 运行。REELSHORT_LOG_FILE 默认 logs/backend.log（相对 WORKDIR /app），
# reelshort.system.logs.root 默认 logs，两者都落在上面已授权的 /app/logs。
USER reelshort
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
