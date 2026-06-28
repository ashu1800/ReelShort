# App 本地联调 Profile 设计

## 背景

Android App 默认访问 `http://10.0.2.2:8080/api/app`，雷电模拟器可以通过该地址访问 Windows 本机后端。当前后端默认 profile 依赖本机 PostgreSQL 和 Flyway validate，开发机没有数据库时难以快速启动，阻碍 App 联调。

## 目标

- 新增后端 `app-dev` profile，使用本地 H2 文件数据库，便于快速启动 Spring Boot。
- 保持内容源地址为本机 Flask：`http://127.0.0.1:5000`。
- 提供 PowerShell 脚本启动 content-provider、backend，并构建安装 Android APK 到雷电模拟器。
- 文档明确雷电访问本机后端使用 `10.0.2.2:8080`。
- 支持通过脚本参数覆盖本地后端和内容源端口，避免 Windows/WSL 端口占用阻塞联调。

## 非目标

- 不替代 Docker Compose/生产部署。
- 不新增后端业务接口。
- 不伪造内容源业务数据。
- 不引入复杂进程管理。

## 方案

后端新增 `application-app-dev.properties`：

- `spring.datasource.url=jdbc:h2:file:./data/app-dev/reelshort;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`
- `spring.jpa.hibernate.ddl-auto=update`
- `spring.flyway.enabled=false`
- `reelshort.rate-limit.enabled=false`
- `reelshort.content-provider.base-url=http://127.0.0.1:5000`

脚本 `infra/scripts/start-app-local-dev.ps1`：

- 检查 Android SDK、雷电 adb、Gradle wrapper 和目标端口。
- 写入 `android-app/local.properties`。
- 后台启动 Flask 内容源。
- 后台启动 Spring Boot `app-dev` profile。
- 构建 debug APK，使用 `reelshortApiBaseUrl` Gradle 属性写入模拟器访问后端的地址。
- 安装并启动雷电 App。
- 输出进程信息和访问地址。

## 验证策略

- 增加 Spring Boot profile 启动测试，证明 `app-dev` profile 能加载上下文。
- 运行后端测试、content-provider 测试、Android `app-core` 测试和 APK 编译。
- 使用雷电 adb 安装并启动 APK，检查无 `FATAL EXCEPTION`。
