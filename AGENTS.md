# 项目概述

ReelShort 是一个长期单机部署的聚合播放平台，包含 Android 原生 App、后台管理网站、Spring Boot 模块化单体后端、同机自部署 Flask ReelShort 内容源服务、PostgreSQL 和 Redis 数据层。

项目定位是聚合播放平台，不建设自有视频上传、转码和 CDN 分发链路。系统核心资产是用户体系、内容聚合、观看行为、积分账户、后台运营和内容源适配能力。

## 模块结构

- `android-app`：Android 原生客户端，使用 Kotlin、Jetpack Compose、Material3 和 Material Icons 建立登录、首页、搜索、详情、播放、账户页面骨架；Compose UI 已接入 `AppStateController`/`AppUiState`，并具备深色影院感视觉基础层；首页、搜索和详情海报支持 Coil 加载内容封面，并保留渐变 fallback；首页、搜索和详情具备可测试的内容空态文案与展示；播放页接入 Media3/ExoPlayer 播放 Spring Boot 返回的合法 HTTP/HTTPS 媒体 URL，并将播放器进度同步到 `PlaybackState`；播放页展示 25/50/75/100 观看奖励阶段提示但不自动上报；账户页提供本地 API 连接诊断，展示当前 Spring Boot API 地址并可手动检查 `/api/system/health`；纯 Kotlin `app-core` 模块承载 Spring Boot API 配置、OkHttp 客户端接口实现、会话存储边界、Repository、数据源边界、共享模型、播放状态、UI action facade 和可测试的 UI 状态控制层，后续接入平台持久化能力。
- `admin-web`：后台管理网站，使用 Vue、Element Plus、Pinia、Vue Router 和 Axios，包含管理员登录、会话、基础导航、用户运营操作、订单管理、内容缓存刷新、系统配置、运行诊断、异常告警、系统日志和审计日志视图。
- `backend`：Spring Boot 模块化单体后端，按 `auth`、`user`、`content`、`watch`、`points`、`order`、`payment`、`admin`、`system` 等业务模块组织；数据库 schema 由 Flyway 迁移管理，JPA 默认只做结构校验。
- `backend/content`：内容聚合 API、内容源适配、首页货架、剧集详情、PostgreSQL 内容/分集缓存和后台缓存刷新能力。
- `backend/order`：充值订单基础模块，包含 App 创建/查询订单、后台订单查询、订单状态边界、内部结算入账边界和商业化预留；当前不接入真实支付渠道。
- `backend/payment`：支付回调适配模块，包含内部模拟支付回调、共享密钥校验、支付事件幂等记录、后台支付事件查询和调用订单结算服务；当前不接入真实支付平台。
- `backend/admin`：后台管理 API，包含持久化管理员账号、角色权限、后台 Token 鉴权、用户查询、用户状态管理、积分调整、观看记录、积分流水和后台操作审计日志查询。
- `backend/system`：后端通用基础设施，包含统一响应、请求 ID、错误处理、用户级并发协调、后台系统配置、后台运行诊断、异常告警、受限系统日志查看，以及可配置内存/Redis 存储的后端限流。
- `content-provider`：第三方 ReelShort Flask API 的同机部署和适配层，提供搜索、货架、分集和播放地址内部端点，Spring Boot 通过统一 `ContentProvider` 接口调用；首页货架兼容 ReelShort 新版 Next.js `en.json` fallback 数据结构。
- `infra`：单机部署、进程管理、Nginx、PostgreSQL、Redis、日志、PostgreSQL/配置备份恢复脚本、静态校验和恢复演练文档。
- `docs`：架构设计、接口说明、部署说明和阶段计划。

## 变更历史

[2026-06-29] content-provider - 分集加载在旧版 movie JSON 404 时回退到新版 `getBookInfo` 接口，修复首页新版内容点击“查看”返回 404。
[2026-06-29] android-app/state - 首页和账户页切换改为优先显示内存缓存，并在后台静默刷新替换数据。
[2026-06-29] android-app/loading-ui - 将页面内横条加载提示改为根层居中弹窗，由 `isLoading` 自动显示和消失。
[2026-06-29] android-app/account-ui - 移除主页面全局顶部栏，将退出入口收敛到账户页，并将账户页重构为“我”页式个人信息与分组入口布局。
[2026-06-29] content-provider - 修复 ReelShort 首页货架新版 `en.json` fallback 数据解析，旧 `/37/recommend.json` 404 或空结果时仍可返回首页资源。
[2026-06-29] android-app/errors - 将错误提示从居中弹窗改为顶部滑入式非阻塞提示条，2 秒后自动消失并支持点击关闭。
[2026-06-29] android-app/auth-flow - 登录/注册成功后即保留会话并进入首页，首页推荐加载失败改为内容错误提示，不再阻断认证结果。
[2026-06-29] android-app/errors - 将 App 错误展示从页面内横幅改为全局弹窗，网络层提取后端错误 `message` 并隐藏原始 JSON。
[2026-06-29] android-app/config - 将默认 App API 地址切换到新服务器 `http://66.42.99.110:18080/api/app`，并允许 HTTP 明文访问用于当前单机部署验证。
[2026-06-28] android-app/api-diagnostics - 实现 App 本地 API 连接诊断，增加健康检查 URL 推导、OkHttp 未鉴权探测和账户页诊断面板。
[2026-06-28] android-app/watch-rewards - 实现播放页观看奖励阶段提示，增加阶段文案 helper、可上报提示和手动上报边界。
[2026-06-28] android-app/player-progress - 实现 Android Media3 播放进度同步，增加播放器位置轮询、秒级转换 helper 和现有观看上报边界接线。
[2026-06-28] android-app/player - 实现 Android Media3 播放器基础层，接入 ExoPlayer、PlayerView、播放 URL 判定和占位 fallback。
[2026-06-28] android-app/content-states - 实现 Android 首页、搜索和详情内容空态体验，增加可测试文案 helper 与 Compose 接线。
[2026-06-28] android-app/images - 实现 Android 内容封面图基础层，接入 Coil Compose、网络权限、封面展示和 fallback 海报。
[2026-06-28] android-app/ui - 设计并实现 Android 视觉基础层，增加深色影院感主题、Material Icons 导航、关键页面重构和雷电模拟器视觉验收。
[2026-06-27] android-app/config - 增加 debug 构建 API base URL 覆盖能力，联调脚本可按本地端口生成模拟器访问地址。
[2026-06-27] app-dev - 增加后端 H2 本地联调 profile、雷电模拟器启动脚本和 App 本地联调文档。
[2026-06-27] android-app/session - 增加纯 Kotlin `FileSessionStore`，Android 组合根使用 `filesDir/reelshort-session.json` 持久化登录会话。
[2026-06-27] android-app/player-ui - 将 Compose 播放页绑定 `PlaybackState`，增加模拟进度、刷新播放地址和当前进度上报入口。
[2026-06-27] content-provider - 增加自动发现 build id 的 404 失效恢复和一次重试，显式配置 build id 保持固定。
[2026-06-27] android-app/playback - 增加纯 Kotlin 播放状态边界、播放进度本地更新、观看上报同步和播放地址刷新。
[2026-06-27] system/alerts - 增加运行诊断异常告警、后台确认接口、审计记录和后台 Web 告警页。
[2026-06-27] system/logs - 增加后台系统日志查看 API、文件范围限制和后台 Web 日志页。
[2026-06-27] android-app/ui - 将 Compose UI 接入 `AppStateController`/`AppUiState`，移除页面本地 sample 状态源。
[2026-06-27] system/runtime - 增加后台运行诊断 API、依赖状态检查和后台 Web 诊断页。
[2026-06-27] infra/backup - 增加单机 PostgreSQL/配置备份恢复脚本、静态校验和恢复演练文档。
[2026-06-27] backend/db - 增加 Flyway 初始迁移、默认 JPA schema validate 和数据库迁移验证测试。
[2026-06-27] android-app/session - 增加纯 Kotlin 会话存储边界、Repository Token 恢复和状态控制器启动恢复/登出流程。
[2026-06-27] android-app/state - 增加纯 Kotlin UI 状态控制层、Repository 数据源边界和 JVM 状态流测试。
[2026-06-27] android-app/http - 增加 OkHttp Spring Boot API 客户端、序列化 DTO、Bearer Token 注入和 MockWebServer JVM 测试。
[2026-06-27] android-app/api - 增加纯 Kotlin `app-core`、Spring Boot API 客户端接口、协程 Repository 边界和 JVM 单元测试。
[2026-06-27] admin/dashboard - 增加后台控制台聚合摘要 API、`DASHBOARD_READ` 权限和后台 Web 单接口控制台。
[2026-06-27] admin/session - 增加后台 Token 登出撤销、撤销/过期 Token 清理和后台 Web 登出调用。
[2026-06-27] auth/session - 增加 App Token 过期、登出撤销和过期/撤销 Token 清理。
[2026-06-27] system/rate-limit - 增加 Redis 限流计数存储、存储模式配置和 Compose Redis 接线。
[2026-06-27] android-app - 增加 Compose 核心 UI 骨架、页面状态和阶段 1 播放闭环占位。
[2026-06-27] infra/deploy - 增加 backend、admin-web、content-provider 容器构建文件、Nginx 配置、Compose 环境模板和单机部署说明。
[2026-06-27] payment/admin-web - 增加后台支付事件查询 API、`PAYMENT_EVENT_READ` 权限和后台支付事件列表视图。
[2026-06-27] payment/order - 增加支付回调事件级幂等锁，并将充值订单金额校验与结算合并到订单锁内执行。
[2026-06-27] payment - 增加内部模拟支付回调、共享密钥校验、支付事件记录、金额校验和订单结算调用。
[2026-06-27] order/points - 增加充值订单内部结算入账边界、`PAID` 状态转换和 `RECHARGE_ORDER` 积分流水。
[2026-06-27] admin-web/order - 增加后台订单管理视图、订单 API 客户端、侧栏入口和控制台订单指标。
[2026-06-27] order - 增加充值订单基础模块、App 订单创建/查询、后台订单查询和 `ORDER_READ` 权限。
[2026-06-27] content-provider - 增加 Flask ReelShort 内容源端点、上游 client、错误映射和 pytest 契约测试。
[2026-06-27] admin-web - 增加用户详情、状态变更、积分调整、观看/积分记录、系统配置编辑和内容货架刷新操作。
[2026-06-27] admin-web - 增加后台登录、会话持久化、路由守卫、用户列表、内容缓存和审计日志基础视图。
[2026-06-27] content/cache - 增加 App 剧集详情接口、分集列表 PostgreSQL 缓存和内容源失败缓存兜底。
[2026-06-27] admin/rbac - 增加持久化后台账号、角色、权限、默认超级管理员引导和后台接口权限校验。
[2026-06-26] system/rate-limit - 修复客户端 IP 解析边界，仅信任本机、内网或链路本地代理转发头。
[2026-06-26] system/rate-limit - 增加单机内存限流、敏感接口默认规则、统一 429 响应和 Redis 替换边界。
[2026-06-26] content/cache - 增加内容货架接口、PostgreSQL 内容缓存、后台缓存状态和货架刷新审计。
[2026-06-26] system/config - 增加后台系统配置接口、配置白名单、配置更新审计，并将观看阶段奖励积分接入配置。
[2026-06-26] admin/points - 增加后台积分调整、`ADMIN_ADJUSTMENT` 积分流水和后台操作审计日志。
[2026-06-26] admin - 增加后台管理员登录、后台 Token 鉴权、用户查询、状态管理、观看记录和积分流水查询。
[2026-06-26] points - 增加 App 积分账户、积分流水查询和观看阶段奖励，使用领取记录唯一约束保障幂等。
[2026-06-26] watch - 增加 App 观看进度上报、观看历史查询、用户隔离和幂等更新记录。
[2026-06-26] auth/security - 增加 App Bearer Token 鉴权、Token 哈希持久化、当前用户上下文和受保护内容接口。
[2026-06-26] auth/user - 增加普通用户注册登录、用户状态、BCrypt 密码哈希和 Auth API 文档。
[2026-06-26] docs - 同步内容源 404 与 502 错误分层说明，保持 API 文档与后端实现一致。
[2026-06-26] backend - 修复内容 API 错误分层，补充参数校验、内容源错误映射和 HTTP 客户端超时配置。
[2026-06-26] backend - 补齐 App 内容剧集列表和播放地址入口，继续保持 Spring Boot 作为唯一业务入口。
[2026-06-26] backend - 增加统一 API 响应、统一错误结构、请求 ID、内容源适配接口和内容搜索入口。
[2026-06-26] project - 增加 `.worktrees/` 忽略规则，用于后续隔离功能开发。
[2026-06-26] scaffold - 创建 backend、admin-web、android-app、content-provider、infra、docs 项目骨架。
[2026-06-26] project - 初始化 Git 仓库和项目基础目录，补充根 README 与忽略规则。
[2026-06-26] docs - 初始化项目说明和总体模块结构，补充 ReelShort 聚合播放平台架构边界。
