# App 本地联调

本流程用于在 Windows 本机启动 Flask 内容源、Spring Boot 后端，并把 Android APK 安装到雷电模拟器。Android App 默认访问：

```text
http://10.0.2.2:8080/api/app
```

雷电模拟器会把 `10.0.2.2` 转发到 Windows 本机，因此后端默认监听 `127.0.0.1:8080`。如果本机 `8080` 已被占用，脚本可以用 `-BackendPort` 改到其他端口，并在构建 APK 时同步写入模拟器访问地址。

## 前置条件

- Android SDK 已安装到 `%LOCALAPPDATA%\Android\Sdk`，包含 `platform-tools`、`platforms;android-35`、`build-tools`。
- 雷电模拟器已启动，默认 adb 路径：`C:\leidian\LDPlayer14\adb.exe`。
- Python 依赖已安装：`content-provider\requirements.txt`。
- 本流程使用后端 `app-dev` profile，不需要本机 PostgreSQL。
- 默认情况下，本机 `5000` 和 `8080` 端口未被其他进程占用；如有占用，可使用脚本参数改端口。

## 启动

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/start-app-local-dev.ps1
```

如果 `8080` 已被 WSL、系统代理或其他服务占用：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/start-app-local-dev.ps1 -BackendPort 18080
```

如果 `5000` 也被占用：

```powershell
powershell -ExecutionPolicy Bypass -File infra/scripts/start-app-local-dev.ps1 -BackendPort 18080 -ContentProviderPort 15000
```

脚本会执行：

- 写入被 Git 忽略的 `android-app/local.properties`。
- 检查内容源和后端端口是否空闲。
- 启动 `content-provider`。
- 等待 `content-provider` `/health` 返回 `UP`。
- 使用 `app-dev` profile 启动后端。
- 等待后端 `/actuator/health` 返回 `UP`。
- 构建 `app-debug.apk`，并通过 `reelshortApiBaseUrl` 写入 `http://10.0.2.2:<BackendPort>/api/app`。
- 安装并启动雷电模拟器中的 App。

## 访问地址

默认端口：

- 后端健康检查：`http://127.0.0.1:8080/actuator/health`
- App API：`http://127.0.0.1:8080/api/app`
- 模拟器访问 App API：`http://10.0.2.2:8080/api/app`
- 内容源健康检查：`http://127.0.0.1:5000/health`

使用 `-BackendPort` 或 `-ContentProviderPort` 时，将上面的 `8080` 或 `5000` 替换为实际参数值。

## 停止

脚本结束时会输出 `Stop-Process` 命令，使用该命令停止后台启动的 content-provider 和 backend 进程。

## 数据库

`app-dev` profile 使用 H2 文件数据库：

```text
backend/data/app-dev/reelshort
```

该目录只用于本地联调，不作为生产数据源。
