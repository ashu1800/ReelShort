# 项目概述

ReelShort 是一个长期单机部署的聚合播放平台，包含 Android 原生 App、后台管理网站、Spring Boot 模块化单体后端、同机自部署 Flask ReelShort 内容源服务、PostgreSQL 和 Redis 数据层。

项目定位是聚合播放平台，不建设自有视频上传、转码和 CDN 分发链路。系统核心资产是用户体系、内容聚合、观看行为、积分账户、后台运营和内容源适配能力。

## 模块结构

- `android-app`：Android 原生客户端，用户可见 App 品牌为 `ShortLink`，使用深色金色自适应 launcher icon；使用 Kotlin、Jetpack Compose、Material3 和 Material Icons 建立游客首页、搜索、详情、播放、账户和登录/注册底部面板；Compose UI 已接入 `AppStateController`/`AppUiState`，默认启动进入首页，未登录可浏览内容，播放和账户数据等受保护动作触发底部认证面板；认证 UI 使用国家区号、手机号、密码和 6 位短信验证码，公开注册只完成验证码流程，登录账号由内部开户注册接口生成，认证弹层默认进入登录模式、注册作为次级入口并按模式切换单一主 CTA，改密验证码走受保护接口且改密成功后清除本机会话和记住密码；首页、搜索和收藏入口点击短剧后加载分集并直接进入播放，未登录时停留原列表弹出认证面板，登录成功后自动继续播放；已登录用户点击短剧时优先按后端观看历史恢复最近观看集数，已看完集数默认续到下一集；首页、搜索和详情海报支持 Coil 加载内容封面，并保留渐变 fallback；首页和搜索海报网格按可用宽度自适应列数，海报卡合并 TalkBack 描述并在大字体下降低覆盖文案密度；详情页展示短剧简介，分集行展示上游标题和分集简介并在缺失时回退短剧简介；播放页接入 Media3/ExoPlayer 播放 Spring Boot 返回的合法 HTTP/HTTPS 媒体 URL，播放器采用 9:16 竖屏优先布局、自动播放并从历史快照位置恢复，视频首次 ready 前显示封面加载过渡，缓冲时显示紧凑加载状态，播放失败时提供重试、下一集和返回操作，底部提供选集入口和分集弹层，选集格子展示当前集、已看和观看进度状态；播放页在 25/50/75/100 观看奖励阶段自动静默上报，并在播放器右上角展示圆形奖励进度状态；账户页采用会员中心式“我的”布局，包含身份摘要卡、2 列快捷业务入口、继续观看海报卡、冷钱包、提现、积分交易、改密、银行卡占位和低优先级语言/诊断/退出分组，账户操作串行防重复提交，钱包解绑、提现和转账需二次确认，表单默认完全展开并支持滚动、大字体、IME 和系统栏安全边距，短信倒计时仅在发送成功后启动，银行卡占位不再收集敏感信息；冷钱包绑定、更换或解绑成功后关闭操作弹层并返回 Me，资金类表单提交失败时保留弹层和用户输入；全局顶部消息按成功、错误和信息使用不同语义色与 TalkBack 实时播报；纯 Kotlin `app-core` 模块承载 Spring Boot API 配置、OkHttp 客户端接口实现、会话存储边界、记住密码凭据存储边界、Repository、数据源边界、共享模型、播放状态和可测试的 UI 状态控制层；生产默认 API 地址为 `https://shortlink.hjj888.cc/api/app`；Android 层使用 AndroidX Security Crypto 加密保存登录会话和记住密码凭据，加密初始化失败时删除旧明文会话并仅保留进程内会话，不再降级写入明文 Token；同时关闭 App 自动备份和全局明文流量。
- `admin-web`：后台管理网站，使用 Vue、Element Plus、Pinia、Vue Router 和 Axios，包含管理员登录、会话、基础导航、用户运营操作、BLACKLISTED 状态管理、提现申请人工审批、用户积分交易记录、订单管理、内容缓存单语言刷新、双语货架刷新、货架健康状态和刷新任务状态、系统配置、运行诊断、内容源结构化诊断事件、异常告警、系统日志和审计日志视图。
- `backend`：Spring Boot 模块化单体后端，按 `auth`、`user`、`content`、`watch`、`points`、`wallet`、`withdrawal`、`order`、`payment`、`admin`、`system` 等业务模块组织；App 账号改为非中国大陆手机号密码登录，公开注册生成随机 6 位验证码并通过 AccountManager 供应商短信回调写入验证码系统但不创建可登录账号，内部手机号开户注册由超级 Token 保护并支持单账号和批量开户；公开短信接口仅允许注册用途，AccountManager 未售出手机号返回 `account_not_found` 时发送接口对 App 假成功但立即作废验证码，验证码重复发送会失效旧码且只能消费一次，改密成功后撤销用户全部未撤销 Token；生产启动会 fail-closed 校验管理员 BCrypt 哈希和支付回调密钥，固定开发值只允许显式 `app-dev` 或测试 profile；数据库 schema 由 Flyway 迁移管理，JPA 默认只做结构校验。
- `backend/content`：内容聚合 API、内容源适配、首页货架、剧集详情、PostgreSQL 内容/分集缓存、刷新运行记录和后台缓存刷新能力；App 内容接口支持 `en` 与 `zh-TW` locale 参数且缓存按 locale 分桶；首页和货架读取优先使用自有 PostgreSQL 元数据缓存，后台刷新负责主动更新片库并记录来源、locale、状态、耗时、数量和错误，支持单语言刷新和全部支持 locale 批量刷新，后台缓存状态按缺失、空结果、错误、过期和健康计算货架健康信号；搜索和分集接口成功拉取后继续回填标题、封面、简介、集数和分集元数据，搜索上游失败时按当前 locale 从自有元数据缓存兜底；首页、搜索、货架、短剧详情和剧集列表允许游客访问，播放地址仍要求 App Bearer Token 并按需向上游获取，播放地址缓存只作为可配置短 TTL 的内容源 5xx 兜底。
- `backend/order`：充值订单基础模块，包含 App 创建/查询订单、后台订单查询、订单状态边界、内部结算入账边界和商业化预留；当前不接入真实支付渠道。
- `backend/payment`：支付回调适配模块，包含内部模拟支付回调、共享密钥校验、支付事件幂等记录、后台支付事件查询和调用订单结算服务；当前不接入真实支付平台。
- `backend/admin`：后台管理 API，包含持久化管理员账号、角色权限、后台 Token 鉴权、用户查询、用户状态管理、积分调整、观看记录、积分流水和后台操作审计日志查询。
- `backend/system`：后端通用基础设施，包含统一响应、请求 ID、错误处理、用户级并发协调、后台系统配置、后台运行诊断、内容源结构化诊断快照、异常告警、受限系统日志查看，以及可配置内存/Redis 存储的后端限流。
- `backend/watch`：App 观看进度、观看历史和单集播放快照接口；快照返回当前用户单集历史位置、进度和已领取奖励阶段，用于播放前初始化奖励状态和续播位置。
- `backend/points`：积分账户、观看奖励、后台积分调整、充值入账和用户间积分交易模块；积分交易按完整手机号账号归一化接收方，不扣手续费，并基于可用积分执行同事务扣减和入账；积分余额增加和入账路径带整型溢出保护，接收方溢出时转账整体回滚。
- `backend/wallet`：App 冷钱包绑定、更换、解绑和银行卡占位模块；冷钱包网络固定为 TRC20，钱包地址允许多用户共享，绑定类操作使用 AccountManager 回调短信验证码，钱包地址按 Tron Base58Check 校验，银行卡提交始终返回不支持且不落库。
- `backend/withdrawal`：积分提现申请和后台人工审批模块；提交提现时冻结可用积分，后台确认 TRC 转账并录入 tx hash 后正式扣减积分，拒绝时释放冻结积分；审批和拒绝使用数据库锁保护提现单和积分账户，重复处理返回业务错误。
- `backend/operations`：内部运营 API 模块，使用 `X-Internal-Super-Token` 保护运营脚本调用；支持查询用户积分快照、获取可领取观看奖励的视频任务，并按用户、短剧、集数和进度模拟观看上报，复用现有观看奖励幂等逻辑和后台审计日志。
- `content-provider`：第三方 ReelShort Flask API 的同机部署和适配层，提供搜索、货架、分集、播放地址和运行诊断内部端点，Spring Boot 通过统一 `ContentProvider` 接口调用；内部接口支持 `en` 与 `zh-TW` locale 参数，首页推荐兼容 ReelShort 新版 Next.js locale fallback 数据结构，并通过带硬上限的可配置搜索关键词扩展片库数量，尽量映射短剧简介、分集标题和分集简介字段；上游 HTTP 请求按总超时、最大响应字节数和最大重定向次数限制资源，并逐跳拒绝私网/回环目标；运行诊断记录搜索空结果、Next data 404、播放页解析失败和播放地址缺失等上游异常摘要。
- `infra`：单机部署、进程管理、Nginx、PostgreSQL、Redis、日志、PostgreSQL/配置备份恢复脚本、静态校验和恢复演练文档；生产 Compose 仅在内部网络开放 PostgreSQL、Redis、backend 和 content-provider，数据层宿主访问必须使用 loopback-only 调试 override，公网 Nginx 拒绝 `/api/internal`；配置备份默认排除 `.env`，显式启用时使用 Windows DPAPI `CurrentUser` 加密并收紧 ACL；服务器宿主机 Nginx 接入 `shortlink.hjj888.cc`，HTTPS 证书由 Certbot/Let's Encrypt 管理，并反向代理公开 `/api/` 到后端、其他路径到后台 Web。
- `scripts`：本地发布和验收脚本，包含发布质量基线验证、脚本回归测试和 Android 模拟器 UI smoke 截图验收；发布基线同时检查工作区、暂存区 whitespace 和未跟踪文件，只有显式开关才允许跳过工作树完整性检查。
- `docs`：架构设计、接口说明、部署说明和阶段计划。

## 变更历史

[2026-07-13] android-app/ux - 加固账户提交去重与资金确认、可滚动弹层、大字体和横屏布局、类型化消息、成功后短信倒计时、输入键盘、银行卡占位和海报无障碍语义。
[2026-07-13] android-app/account-ui - 冷钱包绑定、更换和解绑成功后通过状态事件关闭操作弹层并返回 Me，失败时继续保留弹层和用户输入。

[2026-07-12] backend/infra - 生产启动改为 fail-closed 校验管理员 BCrypt 哈希和支付回调密钥，生产 Compose 移除数据层宿主端口并拒绝公网 `/api/internal`，本地数据层调试仅允许 loopback override。
[2026-07-12] android-app - Android 安全存储初始化失败时删除旧明文会话并仅使用进程内会话，不再把 Bearer Token 降级持久化到明文文件。
[2026-07-12] content-provider - 上游请求新增公网目标逐跳校验、总超时、响应体字节上限和重定向次数上限，并补齐部署环境变量。
[2026-07-12] infra/backup - 配置备份默认排除 `.env`，显式启用时通过 Windows DPAPI `CurrentUser` 加密；Windows 使用 owner-only ACL，非 Windows 数据库备份使用 POSIX 最小权限，并在权限或备份命令失败时安全终止。
[2026-07-12] scripts/release - 发布基线新增暂存区 whitespace 与未跟踪文件检查，并提供显式跳过工作树检查的验证入口。
[2026-07-12] docs/security - 新增全面代码审查问题的安全加固设计与实施计划，覆盖生产密钥、数据层网络、Android 会话、内容源请求、备份和发布基线边界。

[2026-07-10] infra/backend - 短信验证码 AccountManager 回调地址切换为 `https://account.hjj888.cc/api/v1/supplier/sms`，后端回调请求固定发送 `ShortLinkBackend/1.0` User-Agent 以兼容网关校验。
[2026-07-10] android-app/infra - 生产 App API 默认域名切换到 `https://shortlink.hjj888.cc/api/app`，迁移部署目标域名同步调整为 `shortlink.hjj888.cc`。
[2026-07-10] backend/auth - 内部手机号开户注册新增批量接口 `/api/internal/users/register-phone/batch`，支持逐条返回成功 Token 或失败原因并保留单账号接口兼容。
[2026-07-10] backend/operations - 新增内部运营模拟观看奖励 API，支持查询用户积分、获取奖励视频任务和复用真实观看上报路径发放幂等奖励。
[2026-07-10] docs/operations - 新增运营侧内部模拟观看奖励设计与实施计划，规划按用户、短剧、集数和进度复用现有观看奖励幂等逻辑。
[2026-07-10] backend/auth - AccountManager 未售出手机号 `account_not_found` 调整为验证码发送假成功，同时立即作废本地验证码，后续验证仍返回验证码错误。
[2026-07-10] backend/android-app/infra - 短信验证码从固定模拟码切换为随机 6 位码，并通过 AccountManager 供应商短信回调写入验证码；回调失败时发送验证码接口返回业务错误，Android 移除固定验证码提示文案。
[2026-07-08] infra/backend - Docker Compose backend 服务新增 `REELSHORT_INTERNAL_SUPER_TOKEN` 环境变量注入，并已在服务器部署随机超级 Token 用于内部手机号开户注册。
[2026-07-08] android-app/branding - App 用户可见品牌改为 ShortLink，并新增短视频辨识优先的深色金色自适应 launcher icon。
[2026-07-08] android-app/backend/auth - 注册验证码发送改为后端成功后触发倒计时，错误验证码和注册 400 返回本地化友好文案，并补充公开短信与错误验证码后端契约测试。
[2026-07-08] android-app/auth-ui - 认证弹层拆分为登录/注册双模式，默认登录且注册为次级入口，商业级认证流程通过单一主 CTA 和模式切换收口。
[2026-07-08] android-app/backend/admin-web - 修复商业化审查问题：改密后撤销旧 Token，短信验证码用途收紧和一次性消费，TRC 钱包 Base58Check 校验，提现审批锁和积分溢出保护，App 移除旧 username 登录入口、改密清会话并保留失败表单，后台展示提现审批业务错误。
[2026-07-07] android-app/backend/admin-web - 商业化账户体系改为手机号密码登录和公开短信模拟注册，新增内部超级 Token 手机号开户注册、冷钱包、提现冻结审批、积分交易、改密和黑名单限制能力。
[2026-07-07] scripts/android-ui - 新增 Android 模拟器 UI smoke 截图验收脚本，覆盖首页、Me 页、继续观看区域和播放器或登录面板。
[2026-07-07] android-app/account-ui - Me 页继续观看预览升级为可点击海报卡，并为前 2 条观看记录补充短剧封面元数据缓存。
[2026-07-07] android-app/player-ux - 播放器选集弹层新增当前集、已看和观看百分比状态，复用已有观看历史提升切集识别效率。
[2026-07-06] android-app/player-ux - 播放器新增封面加载过渡、紧凑缓冲状态和播放失败恢复操作，减少黑屏和失败无反馈。
[2026-07-06] backend/content-cache - 播放地址缓存兜底增加可配置短 TTL，默认 10 分钟，避免长期返回过期上游视频 URL。
[2026-07-06] backend/admin-web/content-cache - 后台内容缓存新增双语货架刷新入口，按 locale 返回成功/失败结果并复用刷新运行记录。
[2026-07-06] backend/admin-web/content-cache - 内容缓存状态新增货架健康信号，后台按 MISSING、EMPTY、ERROR、STALE、HEALTHY 标签展示每个货架和语言的缓存健康。
[2026-07-06] backend/admin-web/runtime - 后端运行诊断新增结构化 content-provider diagnostics 字段，后台运行诊断页展示内容源事件总数、类型计数和最近事件上下文。
[2026-07-06] backend/system-alerts - 内容源运行诊断出现上游异常事件时新增后台 WARNING 告警，并在诊断恢复 clean 后自动解决。
[2026-07-06] content-provider/runtime - 内容源新增 `/diagnostics` 内存诊断端点，后端运行诊断展示内容源异常事件计数，用于定位上游结构变化。
[2026-07-01] backend/content-cache - 修复 locale 缓存迁移对长 `book_id` 的主键长度风险，并将内容刷新运行记录扩展到非内容源异常和数据库写入失败。
[2026-07-01] backend/admin-web/content-cache - 新增内容货架刷新运行记录，后台内容缓存状态按 locale 展示货架健康、视频缓存数量和最近刷新任务。
[2026-07-01] release-baseline - 修复发布验证脚本对原生命令非 0 退出码的漏检问题，并新增脚本级回归测试。
[2026-07-01] release-baseline - 新增发布质量基线 checklist 和统一验证脚本，固化发布前测试、Android 模拟器验收、服务器验收与回滚边界。
[2026-07-01] docs/roadmap - 新增 ReelShort 后续开发路线图，按发布质量、内容源运维、Android 播放体验、商业化和可观测性分阶段推进。
[2026-07-01] android-app/player-ui - 将播放器右上角积分阶段数字优化为本地化奖励进度胶囊，新增积分机制说明弹层和阶段到账轻反馈。
[2026-07-01] android-app/account-ui - 补齐 Me 页继续观看点击续播、积分流水、观看历史和订单底部弹层交互，新增按观看记录加载短剧详情并进入播放器的状态层入口。
[2026-07-01] android-app/account-ui - 将 Me 页重构为会员中心式商业布局，新增身份摘要卡、2 列快捷入口、继续观看预览和低优先级设置分组。
[2026-07-01] android-app/backend/content-provider - 修复 locale 内容缓存 key 长度风险、语言切换状态一致性、全 App 英文/繁中 UI 文案收口、搜索结果优先布局和 provider locale 货架路径优先级。
[2026-06-30] backend/content - 搜索接口在上游搜索失败时按 locale 从自有短剧元数据缓存兜底，避免预设标签因 provider 404 直接空结果。
[2026-06-30] android-app/backend/content-provider - App 支持 English 与繁體中文本机语言切换，搜索页重构为内容发现页，内容接口和 PostgreSQL 缓存按 locale 分桶。
[2026-06-30] android-app/player-state - 播放入口和播放器切集增加请求版本保护，登录后续播复用观看历史选择，并修正 Media3 加载覆盖层判定。
[2026-06-30] android-app/player-resume - 播放器新增视频加载状态，恢复观看入口按历史集数续播，并修复播放进度自动上报链路。
[2026-06-30] android-app/player-navigation - 首页、搜索和收藏短剧入口改为直达首集播放，播放页新增底部选集入口和分集弹层，播放器返回回到来源列表。
[2026-06-30] backend/content-provider - 分集元数据改为 PostgreSQL 缓存优先并支持损坏自愈，推荐货架新增定时刷新，provider 片库搜索移除共享预算并使用确定性请求计划。
[2026-06-30] backend/content-provider - 首页和货架改为 PostgreSQL 元数据缓存优先，provider 片库扩展增加关键词、页数、并发和总请求硬上限。
[2026-06-30] content-provider - 首页推荐在首屏货架基础上聚合 ReelShort 搜索页片库，新增关键词、分页和最大数量环境配置。
[2026-06-30] android-app/social-ui - 播放页点赞、收藏和评论提交改为非阻塞社交请求，不再触发全局居中加载遮罩。
[2026-06-30] infra/nginx - 接入 `reelshort.hjj888.cc` 宿主机 Nginx 反向代理，申请 Let's Encrypt HTTPS 证书并验证域名健康检查。
[2026-06-30] android-app/security-state - 默认 API 切换到 HTTPS 域名，加密保存 Android 登录会话，关闭自动备份和全局明文流量，并防止重复启动恢复或慢请求覆盖当前页面。
[2026-06-29] content-provider - 兼容 ReelShort 当前 `special_desc` 简介字段，并在分集无单集简介时使用短剧简介兜底。
[2026-06-29] android-app/auth-ui - 区分登录、内容、账户和播放错误文案，新增记住密码自动填充和 Android 加密凭据存储。
[2026-06-29] android-app/auth-flow - 修复游客认证后的状态回流，首页加载失败不再清除已恢复会话，账户页登录后回到账户数据。
[2026-06-29] android-app/auth-ui - App 默认进入游客首页，未登录播放或账户受保护动作时弹出底部登录/注册面板，登录成功后继续待播放分集。
[2026-06-29] android-app/detail-player - 详情页展示短剧和分集简介，播放器改为 9:16 竖屏优先、自动播放并从观看快照位置恢复。
[2026-06-29] backend/content-watch - 内容浏览接口放开游客访问，播放地址保留登录保护，并新增单集观看奖励快照接口。
[2026-06-29] content-provider - 内容源映射短剧简介、分集标题和分集简介，供 Spring Boot 和 App 详情页展示。
[2026-06-29] android-app/player - 播放页改为观看奖励阶段自动静默上报，并新增播放器右上角圆形奖励进度状态。
[2026-06-29] android-app/player - 增加 Media3 HLS 播放模块依赖，修复 `.m3u8` 播放地址创建 HlsMediaSource 时缺类闪退。
[2026-06-29] content-provider - 播放地址获取在旧版 `_next/data` 分集 JSON 404 时回退解析新版 `/episodes/{slug}` 页面 `__NEXT_DATA__`。
[2026-06-29] android-app/detail-ui - 分集列表移除每集时长展示，优化集数编号和播放入口样式。
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
