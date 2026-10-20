# 项目概述

ReelShort 是一个长期单机部署的聚合播放平台，包含 Android 原生 App、后台管理网站、Spring Boot 模块化单体后端、同机自部署 Flask ReelShort 内容源服务、PostgreSQL 和 Redis 数据层。

项目定位是聚合播放平台，不建设自有视频上传、转码和 CDN 分发链路。系统核心资产是用户体系、内容聚合、观看行为、积分账户、后台运营和内容源适配能力。

## 模块结构

- `android-app` 资金导航：资金中心作为底部一级 Tab；Me 页不再承载提现/VIP入口，提现、VIP充值及 VIP 充值记录集中在资金中心及其子页面。

- `android-app/player` 奖励显示：播放前圆环按后端结合当前用户十分位尾数和当日剩余额度返回的预计整数入账显示；奖励结算后圆环和 `+N pt` 浮层都使用服务端实际到账积分。

- `backend/content/points` 奖励预计：播放接口的 `estimatedRewardPoints` 按权威时长、当前积分配置、登录用户十分位尾数和当日剩余额度实时计算，不写入内容缓存。

- `android-app`：Android 原生客户端，用户可见 App 品牌为 `ShortLink`，使用深色金色自适应 launcher icon；使用 Kotlin、Jetpack Compose、Material3 和 Material Icons 建立游客首页、搜索、详情、播放、账户和登录/注册底部面板；Compose UI 已接入 `AppStateController`/`AppUiState`，默认启动进入首页，未登录可浏览内容，播放和账户数据等受保护动作触发底部认证面板；认证 UI 使用用户名、至少 8 位密码和 4 位图形验证码，注册通过后直接创建可登录账号并返回 Token，认证弹层默认进入登录模式、注册作为次级入口并按模式切换单一主 CTA，改密只需旧密码与新密码且改密成功后清除本机会话和记住密码；首页、搜索和收藏入口点击短剧后加载分集并直接进入播放，未登录时停留原列表弹出认证面板，登录成功后自动继续播放；已登录用户点击短剧时优先按后端观看历史恢复最近观看集数，已看完集数默认续到下一集；首页、搜索和详情海报支持 Coil 加载内容封面，并保留渐变 fallback；首页和搜索海报网格按可用宽度自适应列数，海报卡合并 TalkBack 描述并在大字体下降低覆盖文案密度；详情页展示短剧简介，分集行展示上游标题和分集简介并在缺失时回退短剧简介；第 8 集及以后统一显示 VIP 锁定状态，非 VIP 点击后在当前详情页或播放器上打开共享 TRC20 付费弹层，收款二维码由 App 本地生成（不再请求第三方二维码服务），游客登录后自动进入付费层，订单确认后自动关闭并继续原目标分集，Me 页复用同一全局弹层；播放页接入 Media3/ExoPlayer 播放 Spring Boot 返回的合法 HTTP/HTTPS 媒体 URL，播放器采用 9:16 竖屏优先布局、自动播放并从历史快照位置恢复，视频首次 ready 前显示封面加载过渡，缓冲时显示紧凑加载状态，播放失败时提供重试、下一集和返回操作，底部提供选集入口和分集弹层，选集格子展示当前集、已看和观看进度状态；播放页约每 15 秒静默保存进度，并在播放完成时按服务端权威时长一次性领取奖励，右上角以积分图标和确定型圆环展示观看进度，圆心显示后端按本集权威时长计算的预计基础积分，点击后继续提供状态说明并以浮层反馈真实到账积分；账户页采用会员中心式“我的”布局，包含身份摘要卡、2 列快捷业务入口、继续观看海报卡、冷钱包、提现、提现记录、改密、银行卡占位、联系客服邮箱和低优先级诊断/退出分组，提现数量输入框失焦且为空时提示到账约 1 天；账户操作串行防重复提交，钱包解绑与提现需二次确认，表单默认完全展开并支持滚动、大字体、IME 和系统栏安全边距，银行卡入口为只读“暂不支持”说明，不再采集卡号、有效期或 CVV；冷钱包绑定、更换或解绑成功后关闭操作弹层并返回 Me，资金类表单提交失败时保留弹层和用户输入；全局顶部消息按成功、错误和信息使用不同语义色与 TalkBack 实时播报；纯 Kotlin `app-core` 模块承载 Spring Boot API 配置、OkHttp 客户端接口实现、会话存储边界、记住密码凭据存储边界、Repository、数据源边界、共享模型、播放状态、本地二维码矩阵编码和可测试的 UI 状态控制层；受保护接口返回 401 时清理本机会话并回到游客态，钱包/提现类“当前密码错误”按 403 处理、不误判为会话失效；已删除 GeoIP 地区封禁死代码（含向第三方 `ipapi.co` 外发 IP）与 GitHub Release 更新客户端；生产默认 API 地址为 `https://shortlink.hjj888.cc/api/app`；Android 层使用 AndroidX Security Crypto 加密保存登录会话和记住密码凭据，加密初始化失败时删除旧明文会话并仅保留进程内会话，不再降级写入明文 Token；同时关闭 App 自动备份和全局明文流量。
- `android-app/release`：通过后端 `/api/app/release/latest`（游客可访问）获取版本清单，后端按需生成短时 COS 预签名下载链接；旧版 `/api/app/update/latest` 与 `/downloads/android/` 兼容链路已删除，只保留 COS 预签名下载方式；App 在私有缓存流式下载 APK 并在 IO 线程计算 SHA-256、搬移文件，展示进度后经文件大小与 SHA-256 双重完整性校验调用 Android 系统安装器（**不再要求发布包带签名**，也不在客户端校验签名；正式签名由 Gradle 打包前的钉死证书门禁保证，安装合法性由 Android 安装器兜底）；发布脚本改用 `aapt` 预检包名与版本，并在上传前后核对 SHA-256 与文件大小；清单解析拒绝 versionCode 非正数或低于当前安装版本的降级包，COS 下载域名只接受精确桶域名 `<bucket>.cos.<region>.myqcloud.com`（不再用 `.myqcloud.com` 后缀匹配）；Debug 与 Release 变体统一使用恢复后的 `android-app/release-signing/shortlink-release.jks` 原正式证书，SHA-256 为 `c1a149d5352c5c313d8d7d995470da31a3bd298281abba914e559747bb45c2f9`，`Debug` 仅表示可调试构建属性，ReelShort 不再读取用户目录的 Debug keystore；Gradle 在所有 APK/AAB 打包前钉死校验证书，发布脚本从 gitignored 本地配置加载 `ANDROID_SIGNING_*`，完成包名/版本预检、SHA-256 与文件大小核对、上传腾讯云 COS（含对象大小回核）并回调后端，详细入口为 `android-app/SIGNING.md`。
- `admin-web`：后台管理网站，使用 Vue、Element Plus、Pinia、Vue Router 和 Axios，包含管理员登录、会话、基础导航、用户运营操作、BLACKLISTED 状态管理、按未来到期时间设置或取消用户 VIP 权益、ERC20 提现申请拒绝与管理员外部钱包打款后的单笔确认、提现申请列表复制收款钱包地址、每 50 积分对应 USDT 的直接换算配置、按今天/昨天/本周/本月/上月及自定义日期区间筛选的 ERC20 打款笔数和金额统计、带失败重试幂等键的积分调整、VIP 订单人工确认/拒绝、内容货架刷新（语言选择器只有 `English`，另有串行刷新当前全部语言入口）、货架健康状态和刷新任务状态、系统配置、运行诊断、内容源结构化诊断事件、异常告警、系统日志和审计日志视图；账号列与类型只使用 `username`，已删除后端并不存在的 `phone*` 字段，也不再提供不存在的“订单管理/支付事件”入口（`GET /api/admin/orders`、`GET /api/admin/payments/events` 仍保留后端接口）；资金敏感系统配置（VIP 收款地址、兑换比例等）保存前有二次确认并展示旧值与新值；管理员仅使用账号密码登录，后台会话与 RBAC 权限控制保持不变；提现确认只要求已登录且具备 `WITHDRAWAL_WRITE`，弹窗明确“不签名、不广播、不核验链上交易”，不接收私钥、交易哈希或二次验证码。
- `backend`：Spring Boot 模块化单体后端，按 `auth`、`user`、`content`、`watch`、`points`、`wallet`、`social`、`withdrawal`、`order`、`payment`、`admin`、`release`、`system` 等业务模块组织；App 账号使用用户名 + 密码 + 图形验证码，`POST /api/app/auth/register` 携带 `username/password/captchaId/captchaAnswer` 并直接创建可登录账号，登录只提交 `username/password`，改密只提交旧密码与新密码且成功后撤销用户全部未撤销 Token；登录对禁用账号也执行一次 BCrypt 比对再判状态，避免账号枚举；图形验证码答案只以 SHA-256 哈希落库、答错立即作废、过期挑战由定时任务清理；手机号/短信验证码、`REELSHORT_SMS_CALLBACK_*`、AccountManager 回调与内部开户注册接口均已删除（详见 `docs/api/auth-user.md` 的历史说明）；生产启动会 fail-closed 校验管理员 BCrypt 哈希和支付回调密钥，固定开发值只允许显式 `app-dev` 或测试 profile；数据库 schema 由 Flyway 迁移管理，JPA 默认只做结构校验，最新迁移 V31 为纯新增列/索引与约束放宽；`release` 模块用 Flyway `app_releases` 表持久化发布记录，`/api/app/release/latest` 游客可访问并返回 COS 预签名下载链接，发布接口 `/api/internal/release/publish` 由超级 Token 保护，同名重发会更新 `versionCode` 且拒绝回退。
- `backend/content`：内容聚合 API、内容源适配、首页货架、剧集详情、PostgreSQL 内容/分集缓存、刷新运行记录和后台缓存刷新能力；内容语言收敛为英文单语，`ContentLocale` 只保留 `ENGLISH("en")`，`locale` 参数保留但任何传入值都解析为 `en`，缓存只保存一套英文元数据；上游调用一律发生在数据库事务之外，写库集中到 `ContentCacheWriter`，同一 `(shelfType, locale)` 的并发 miss 由 `ShelfSingleFlight` 去重，避免未鉴权读路径占用连接池；首页和货架读取优先使用自有 PostgreSQL 元数据缓存，后台刷新负责主动更新片库并记录来源、locale、状态、耗时、数量和错误，后台缓存状态按缺失、空结果、错误、过期和健康计算货架健康信号；搜索和分集接口成功拉取后继续回填标题、封面、简介、集数和分集元数据，搜索上游失败时从自有元数据缓存兜底；首页、搜索、货架、短剧详情和剧集列表允许游客访问，播放地址仍要求 App Bearer Token 并按需向上游获取，播放响应按权威时长和当前积分配置实时返回 `estimatedRewardPoints`，播放地址缓存只作为可配置短 TTL 的内容源 5xx 兜底且不缓存奖励估算。
- `backend/order`：充值与 VIP 订单模块；VIP TRC20 订单保存收款地址、USDT 合约、基础/应付金额和有效期不可变快照，每个用户仅保留一个活动订单，并通过数据库分配锁、活动金额唯一约束和交易哈希唯一约束防止错配与重放；自动确认按订单快照 `(address, contract)` 分组，使用 V23 `vip_transfer_scan_cursors` 持久化 TronGrid fingerprint 和扫描窗口，在分页硬上限后跨轮续扫；人工确认按 txHash 直查 Transfer events，不依赖最近交易窗口；两条路径均查询 receipt 和当前块，复核地址、合约、金额、付款时间、执行状态与真实确认数；原有效期限制付款时间，可配置确认宽限只延后最终过期；人工确认要求已登录管理员具备 `ORDER_WRITE`，自动确认和过期写系统审计，权益交付锁定用户行后续期；管理员人工路径允许对 `EXPIRED` 订单做链上已核验的过期后补单（`recoverExpired` 复用同一套地址/合约/金额/时间窗/确认数复核，审计动作 `VIP_ORDER_RECOVERED_AFTER_EXPIRY`），避免有效期内已付款却因确认延迟无法对账；非法状态转换统一映射为 409 `conflict`；旧充值创建当前保持禁用。
- `backend/payment`：支付回调适配模块，包含内部模拟支付回调、共享密钥校验、支付事件幂等记录、后台支付事件查询和调用订单结算服务；当前不接入真实支付平台。
- `backend/admin`：后台管理 API，包含持久化管理员账号、角色权限、后台 Token 鉴权、用户查询、用户状态管理、VIP 权益设置/取消、积分调整、观看记录、积分流水和后台操作审计日志查询。
- `backend/system`：后端通用基础设施，包含统一响应、请求 ID（只接受 `^[A-Za-z0-9_-]{8,64}$`，否则重新生成）、错误处理（`IllegalStateException` → 409 `conflict` 不回显异常消息，`DataIntegrityViolationException` → 400，`CredentialLockedException` → 429）、用户级并发协调、后台系统配置（资金敏感的 `vip.collection-address` 写入时校验 TRON 地址格式，允许清空）、后台运行诊断、内容源结构化诊断快照、异常告警（按诊断事件水位线判定并每 5 分钟自动评估，恢复即 resolve）、受限系统日志查看、凭证失败锁定（`CredentialAttemptGuard`，默认 5 次 / 15 分钟，失败计数 `REQUIRES_NEW` 独立落库），以及可配置内存/Redis 存储的后端限流（可信代理白名单支持 CIDR，前缀过宽时启动失败）；默认 Hikari 数据库连接池上限为 30，可通过 `REELSHORT_DB_POOL_MAXIMUM_SIZE` 覆盖。
- `backend/watch`：App 观看进度、观看历史和单集播放快照接口；播放接口取得的权威视频时长作为独立元数据缓存，观看完成后按服务端时长触发每集一次性奖励，快照返回当前用户单集历史位置、进度和领取状态；进度只允许前进，乱序或离线补报不会把已看完的集数回退（避免"进度 8% + 已领奖"的永久不一致），标题与章节等元数据仍按最新上报更新。
- `backend/points`：积分账户、按视频时长计算的完成奖励、后台积分调整和充值入账模块；默认每完整 60 秒奖励 1 积分，公平模式保留十分位并以实际整数进位同时写入余额、流水、领取记录和响应，同一用户同一集永久只奖励一次；后台调整强制使用按管理员和用户作用域的幂等键；观看奖励和运营模拟观看共享每日全局规则快照与账号随机向下浮动额度，基础上限默认 1000、全局浮动上限默认 35%，按服务器自然日重置且只限制 `WATCH_REWARD` 自动奖励；积分余额增加和入账路径带整型溢出保护；提现人工确认会写入负数 `WITHDRAWAL` 流水（带扣减后余额快照与 `withdrawal:<申请 ID>` 幂等键），充值入账流水带 `recharge:<订单号>` 幂等键，因此"余额 = 流水累计"在所有入账/出金路径上成立；`point_transactions` 已由 V31 补 `(user_id, created_at DESC)` 索引。
- `backend/wallet`：App 冷钱包绑定、更换、解绑和银行卡占位模块；当前只支持 `ERC20` 钱包（`0x` + 40 位十六进制、拒绝全零地址），钱包地址允许多用户共享；绑定、更换和解绑必须验证当前密码，错误返回 `403 invalid current password`，连续失败触发凭证锁定并返回 429（改密共用同一计数）；银行卡入口已下线，`POST /api/app/wallet/bank-card` 固定返回 410，服务端不再接收/校验/落库卡号、有效期、CVV 或后四位，历史 `bank_card_attempts` 表仅保留数据不再有代码引用。
- `backend/withdrawal`：ERC20 积分提现申请与管理员外部钱包确认模块；默认每 50 积分兑换 0.14 USDT，手续费积分从用户提交积分中扣除，实际 USDT 向下截断到 2 位且最低 10 USDT，最低积分按当前比例、手续费和最低 USDT 动态计算；新申请保存每 50 积分 USDT 快照，历史人民币/美元快照及原金额不重算；提交提现支持可选 `Idempotency-Key` 请求头（≤128 字符）并强制同一用户同时只有一笔 `PENDING`，重复提交返回 409；提交时冻结全部提交积分，人工确认在**同一事务内**扣减冻结积分、写入负数 `WITHDRAWAL` 流水（余额快照 + `withdrawal:<申请 ID>` 幂等键）并标记 `APPROVED`，拒绝时释放冻结积分；新增受审计的 `POST /api/admin/withdrawals/{id}/payout-attempt/void` 作废卡死的 `ACTIVE` attempt（`SIGNING`/`PREPARED` 直接作废，`BROADCASTED` 先只读链上核对——确认达标转 `MANUAL_REVIEW`、查无或失败才作废、状态未定返回 409，作废本身不释放积分）；自动打款执行能力由 `reelshort.withdrawal.payout.enabled`（`REELSHORT_WITHDRAWAL_PAYOUT_ENABLED`，默认 `false`）门禁，关闭时 `WithdrawalPayoutCoordinator`/`WithdrawalPayoutTransactionService`/`WithdrawalPayoutConfirmationService` 不装配，`approve`/`batch-approve`/`batch-preview` 固定返回 410；签名与广播代码仍在 `TronClient`/EVM 客户端中，属未清理的剩余项；统计按 `reviewedAt` 在上海时区汇总已确认 ERC20 提现，支持预设区间与 `CUSTOM + fromDate/toDate`。
- `backend/social`：点赞、收藏和评论模块；真实用户数据仍按 `bookId` 归集，展示接口按 `bookId` 生成稳定分层随机的伪热度，并与真实点赞、收藏、评论合并展示；伪评论集合稳定但每次读取随机打乱顺序，且使用无数字后缀昵称，避免播放器页长期显示全 0 或热度、评论排序过于机械；评论列表分页返回 `{comments, hasMore, page, size}`（`page` 默认 0、`size` 默认 20、上限 50），真实评论按 `created_at desc` 分页且始终排在伪评论之前、伪评论随页裁剪；收藏与评论请求体补 `@Size` 长度约束。**注意：Android 客户端尚未接入分页，仍按数组解析该响应。**
- `backend/operations`：内部运营 API 模块，使用 `X-Internal-Super-Token` 保护运营脚本调用；支持查询用户积分快照、获取可领取观看奖励的视频任务，观看任务按最近一条观看记录续播同一短剧的下一未领取分集（该集未领奖时返回该集本身），该短剧播完或用户没有观看记录时随机取一部该用户从未看过的短剧的第 1 集，再按用户、短剧、集数和进度模拟观看上报，复用现有观看奖励幂等逻辑和后台审计日志；观看任务查询不包裹长事务，内容源现拉时不长期占用数据库连接。
- `content-provider`：第三方 ReelShort Flask API 的同机部署和适配层，提供搜索、货架、分集、播放地址和运行诊断内部端点，Spring Boot 通过统一 `ContentProvider` 接口调用；只支持英文单语（`SUPPORTED_LOCALES = {"en"}`），`locale` 参数保留但只接受 `en`，繁中片库关键词扩展配置已删除；首页推荐兼容 ReelShort 新版 Next.js locale fallback 数据结构，并通过带硬上限的可配置搜索关键词扩展片库数量，尽量映射短剧简介、分集标题和分集简介字段；上游响应字段用 `_as_int`/`_as_list` 容错转换，结构或类型异常统一由异常处理器记录诊断事件并返回 502 而不是 500，`next_episode` 为 best-effort，诊断上下文字段截断；上游 HTTP 请求按总超时、最大响应字节数和最大重定向次数限制资源，并逐跳拒绝私网/回环目标；HTTPS 固定连接到校验后的公网 IP，同时保留原始域名 SNI、证书校验和 Host；生产由 gunicorn 承载并以 `nobody` 运行，除 `/health` 外所有端点要求 `X-Content-Provider-Token`（`CONTENT_PROVIDER_INTERNAL_TOKEN`），未配置该密钥时 fail-closed。
- `infra`：单机部署、进程管理、Nginx、PostgreSQL、Redis、日志、PostgreSQL/配置备份恢复脚本、静态校验和恢复演练文档；生产 Compose 仅在内部网络开放 PostgreSQL、Redis、backend 和 content-provider，数据层宿主访问必须使用 loopback-only 调试 override，公网 Nginx 允许 `/api/internal` 透传到 backend 并由 `X-Internal-Super-Token` 做接口级鉴权；所有服务配置 `restart: unless-stopped` 与 `max-size=10m`/`max-file=3` 日志轮转，nginx 容器 healthcheck 直连 `http://backend:8080/actuator/health`（不走自身 SPA 兜底），backend 容器以非 root 账号 `reelshort` 运行且日志目录为 `/app/logs`，content-provider 以 `nobody` 运行并由 gunicorn 承载；`REELSHORT_TRUSTED_PROXIES`（支持 CIDR）和 `CONTENT_PROVIDER_INTERNAL_TOKEN` 已接入 Compose，前者为必填插值；生产库转储一律落在被 `.gitignore` 忽略的 `infra/backups/`，`backup.ps1` 拒绝绝对路径与 `..` 逃逸并在删除前打印待删目录；配置备份默认排除 `.env`，显式启用时使用 Windows DPAPI `CurrentUser` 加密并收紧 ACL；服务器宿主机 Nginx 接入 `shortlink.hjj888.cc`，HTTPS 证书由 Certbot/Let's Encrypt 管理，并反向代理公开 `/api/` 到后端、其他路径到后台 Web；Android 更新不再依赖静态本地 APK 目录。
- `scripts`：本地发布和验收脚本，包含发布质量基线验证、脚本回归测试和 Android 模拟器 UI smoke 截图验收；发布基线同时检查工作区与暂存区 whitespace、**未提交的已跟踪文件修改**（`-AllowDirtyTree` 显式放行）和未跟踪文件（`-AllowUntrackedFiles` 显式放行），并支持 `-SkipAndroidPackage`（无签名密钥时只跑 JVM 单测）、`-SkipInfraChecks`、`-SkipScriptTests`、`-SkipAdminWebContracts` 分步跳过；发布脚本校验包名、版本号和 versionCode 并核对 APK 的 SHA-256 与文件大小（不再校验签名，正式签名证书由 Gradle 打包前钉死校验），只有显式开关才允许跳过工作树完整性检查；`.github/workflows/ci.yml` 在 push/PR 上运行后端 Gradle 测试、content-provider pytest、admin-web 构建 + 契约测试与 Android JVM 单测（均不需要签名密钥），`.github/workflows/security.yml` 继续用 gitleaks 扫描全历史。
- `docs`：架构设计、接口说明、部署说明和阶段计划。

## 构建与测试命令

所有命令在对应模块目录下运行。Windows 用 `gradlew.bat`，POSIX 用 `./gradlew`。

- `backend`（Spring Boot · Gradle · Java 17）
  - 构建：`./gradlew build`
  - 全部测试：`./gradlew test`
  - 单个测试类/方法：`./gradlew test --tests "com.reelshort.backend.auth.XxxTest"`（可再追加 `--tests "*.methodName"`）
  - 本地联调（H2 文件库、关闭 Flyway 与限流、JPA ddl-auto=update）：`./gradlew bootRun --args='--spring.profiles.active=app-dev'`
  - 生产/profile 非显式 `app-dev` 或测试时，启动会 fail-closed 校验管理员 BCrypt 哈希和支付回调密钥，缺失即失败。
- `admin-web`（Vue 3 · Vite · TypeScript）
  - 安装：`npm install`
  - 开发：`npm run dev`
  - 类型检查 + 生产构建：`npm run build`（实际执行 `vue-tsc -b && vite build`，类型错误会导致构建失败）
  - 仓库未定义独立 lint 脚本；devDependencies 含 `eslint`/`prettier`，按需手动调用。
- `android-app`（Kotlin · Gradle KTS · Compose，含 `app` 与纯 Kotlin `app-core` 两模块）
  - Debug 构建：`./gradlew assembleDebug`
  - `app-core` JVM 单元测试：`./gradlew :app-core:test`
  - Android Lint：`./gradlew lint`
  - 真机/模拟器插桩测试：`./gradlew :app:connectedAndroidTest`
  - 版本由 gradle property 控制：`-PappVersionName=0.4.1 -PappVersionCode=5`
  - 本地后端地址覆盖：`-PreelshortApiBaseUrl=http://10.0.2.2:18080/api/app`
  - Debug 与优化 Release 变体均只使用恢复后的原正式 keystore，并由 `ANDROID_SIGNING_*` 注入；`Debug` 只保留可调试属性。所有 APK/AAB 打包前按 `android-app/SIGNING.md` 校验固定正式证书 SHA-256，禁止生成或使用 Debug 证书及其他签名。
- `content-provider`（Flask · pytest）
  - 安装依赖：`pip install -r requirements.txt`
  - 运行：`python app.py`（默认监听 5000，后端通过 `http://127.0.0.1:5000` 同机调用）
  - 测试：`pytest`（`pytest.ini` 设置 `pythonpath = .`）
- 发布与验收脚本（工作区根运行）
  - Android 正式版发布（对象存储）：`python scripts/publish-android.py [--version X.Y.Z --version-code N --notes "..."]`，交互式构建签名 APK → 上传腾讯 COS → POST 后端；配置见 `.env.example` 和 `docs/deploy/publish-android.md`
  - 发布质量基线：`powershell -ExecutionPolicy Bypass -File scripts/verify-release-baseline.ps1`（Android 改动追加 `-InstallApk`；无签名密钥时加 `-SkipAndroidPackage` 只跑 JVM 单测；有意保留未提交改动时加 `-AllowDirtyTree`）
  - Android UI smoke：`powershell -ExecutionPolicy Bypass -File scripts/android-ui-smoke.ps1`
  - App 本地联调一键脚本：`powershell -ExecutionPolicy Bypass -File infra/scripts/start-app-local-dev.ps1 [-BackendPort 18080]`
  - 备份/恢复：`infra/scripts/backup.ps1` / `restore.ps1`

## 编辑约定与注意事项

- 语言：项目文档与本说明使用简体中文；**内容与后台仅英文单语（`en`）**——`ContentLocale`、Flask `SUPPORTED_LOCALES` 与 Android `AppLanguage` 都只保留英文，`locale` 参数保留但一律解析为 `en`，内容缓存只有一套英文数据；新增内容接口不要再引入第二语言（`zh-TW`、`双语`、`按 locale 分桶` 等表述已失效）。
- 后端分层：包按业务模块组织在 `com.reelshort.backend.<module>`（auth/user/content/watch/points/wallet/withdrawal/order/payment/admin/system/operations/social/security），测试镜像同名包。Spring Boot 是唯一对外业务入口，所有上游内容调用走统一 `ContentProvider` 接口，App 不直连 content-provider。
- 数据库：schema 只由 Flyway 迁移管理（`backend/src/main/resources/db/migration/V*__*.sql`），生产 JPA `ddl-auto=validate`，禁止靠 JPA 自动建表改 schema；新增列/表必须写迁移。
- Android 分层：`app-core` 是无 Android 依赖的纯 Kotlin JVM 模块（API 客户端、Repository、会话/凭据存储边界、UI 状态控制层，可在 JVM 跑单测）；`app` 是 Android 组合根与 Compose UI。新业务逻辑优先落到 `app-core` 以保持可测试性。会话与记住密码凭据经 AndroidX Security Crypto 加密，禁止降级写明文 Token。
- 密钥与机密：`.env`、签名密钥库（`*.jks`/`*.keystore`/`*.p12`）、`local.properties`、`key_*.pub` 均被 `.gitignore` 忽略；CI 的 gitleaks 会扫描全历史（push 与 PR），不要提交任何机密或本地服务器信息。
- 敏感区域先读文档：改认证/安全看 `docs/api/auth-security.md`，改内容缓存看 `docs/api/content-cache.md`，改提现看 `docs/api/withdrawals.md`，改内部运营看 `docs/api/internal-operations.md`，发布流程看 `docs/deploy/release-checklist.md`，安全加固背景看 `docs/plans/` 下最新 security 计划。
- 单机部署边界：生产 Compose 只在内部网络暴露 PostgreSQL/Redis/backend/content-provider，公网仅 Nginx；数据层宿主调试必须用 loopback-only override。App 更新下载只使用后端生成的 COS 预签名链接。

## 变更历史

[2026-10-20] android-flutter/ui + backend/points - 选集面板改为「整季概览条 + 数字网格 + VIP 分段」，锁集边界改由后台 `vip.free-episodes` 下发（后端 `PointAccountResponse` 新增 `freeEpisodes`，与 play 接口门禁同源；客户端 `AppStateController.freeEpisodes` 暴露，`episodeRequiresVip` 取代写死的 `requiresVip()`）；`EpisodeSpine` 从纵向刻度轨收敛为单行概览条，新增 `EpisodeTile` 网格（≥44dp，360dp 每行 6 个）。顺带修掉两个真实缺陷：已看/进行中单元格下划线因 `FractionallySizedBox` 缺 `heightFactor` 一直是 0 高；`_chooseResumeEpisode` 拉到的观看历史被丢弃，导致从首页进播放后选集面板整季显示"未看"。验证：`flutter analyze` 0 issue、`flutter test` 300 全绿、后端 94 类 / 588 用例全绿；生产服务器当时不可达，真机复核改用真实 Roboto 离屏渲染取证。详见 `android-flutter/docs/ui-redesign-notes.md` §12。

[2026-10-19] android-flutter/ui - 重新设计更新弹窗的下载进度信息（提交 `c083821`）：`febfe2c` 只解决了溢出（包 `Flexible` + 单行省略），长字节数随即被截断成 `29% · 16.2 MB / 54.…`——用户看不到剩余总量。改为两行：主行百分比（`titleMedium` 加粗），副行 `14.9 MB of 54.0 MB`（分隔符由 ` / ` 改为 `of`，`bodySmall` 次要色、最多两行、不省略）；同时删除与弹窗标题重复的 `Downloading update`，仅当总大小未知时回退显示该状态文案。测试改为断言「百分比与完整总量都可见」，新增窄宽 + 3.2× 用例。验证：`flutter analyze` 0 issue、`flutter test` 290 用例全绿；雷电模拟器装 0.9.6 触发真实更新，截图确认 `27%` 与 `14.9 MB of 54.0 MB` 完整显示、无溢出。

[2026-10-19] android-flutter/ui - 修复更新弹窗下载进度行溢出（提交 `febfe2c`）：真机截图上弹窗右侧的红色竖排文字不是内容，是 Flutter 的 **RenderFlex 溢出警告**（只在 debug 构建显示）。下载行由 `spaceBetween` 的两个裸 `Text` 组成、都没有宽度约束，`82% · 44.4 MB / 54.0 MB` 一类的长文案把内容撑出弹窗（实测溢出 24 px），红色横幅正好盖住文案。改为把该行抽成公开的 `DownloadProgressRow`，两侧包 `Flexible` + 单行省略（静态文案先让位、进度数字保留剩余空间右对齐），并删除 `_downloadContent` 已不再使用的 `copy` 参数。新增 `test/update/update_dialog_layout_test.dart` 三条（长文案不溢出、未知总大小保留纯金额、160px + 3.2× 极端情况）；变异测试确认去掉 `Flexible` 会立即报 `RenderFlex overflowed by 24 pixels on the right`，与真机现象一致。验证：`flutter analyze` 0 issue、`flutter test` **290 用例全绿**（287 + 3）。

[2026-10-19] content-provider - 修复生产货架刷新永久 404（提交 `5515bfe`，已部署到 `104.143.38.190`）：后台内容缓存页 `recommend` 货架持续 `ERROR / content provider returned 404`，而剧集索引与播放地址缓存均正常。**根因是配置边界 bug，不是上游故障**——生产 env 里 `REELSHORT_NEXT_BUILD_ID=` 为空值，`os.getenv()` 对「已定义但为空」返回 `""` 而非 `None`，构造函数 `build_id is not None` 因此为真、`fixed_build_id` 被错误置为 `True`；`_get_locale_data()` 判断 `if exception.status_code != 404 or self.fixed_build_id: raise`，于是永远走「直接重抛」分支，`_home_shelf_books` 兜底也用同一个（上游已弃用的）build id 请求首页。上游 Next.js 已不再提供 `/{locale}/recommend.json`（首页 `.json` 仍可用），双因叠加导致每 6 小时一次的定时刷新连续失败。**修复**：`from_env()` 与构造函数均把空串归一为 `None`，语义回到「自动发现」；新增 2 条回归（空 env 不被视为固定、失效 build id 在 404 后刷新并走首页兜底），变异测试确认旧实现会失败。**验证**：`pytest` 87 passed（85 + 2）；服务器上以修复语义直接调用 `shelf("recommend")` 返回 500 本；打补丁后文件 SHA-256 与仓库一致（`d953ebfb…be6735`），`py_compile` 通过后重启服务，接口由 404 变为 200/500 本；15:12 的定时刷新自动 `SUCCESS / 500`，`content_shelf_cache` 已写入 `RECOMMEND|ENGLISH`，公网 `GET /api/app/content/shelves/recommend` 返回 `code:0` 与真实书目。回滚备份：`/root/app.py.bak.20261019145143`。**未处理**：`new-release` / `drama-dub` 仍从未刷新（`REELSHORT_CONTENT_REFRESH_SHELVES=recommend` 只配了一个货架，且这两个上游 JSON 同样已 404，启用后会走首页兜底、数据与 recommend 重叠）。

[2026-10-19] android-flutter/ui - 播放器动作栏上移、底部信息区拉满整宽（提交 `741f130`）：动作栏原为独立 `Positioned`，底部固定 `_railBottomInset = 120`，底部文案区为此在右侧留 `_bottomCopyRailInset = 84` 避让内边距，导致标题/简介/进度条/选集按钮都短一截，且动作栏位置写死、简介换成两行或选集按钮消失时对不齐。改为把动作栏与 `_BottomInfo` 放进同一个底部 `Column`（动作栏在上、信息区在下）自然堆叠，动作栏随内容高度自适应上移；底部信息区去掉右侧避让，左右各用 `space4`，四条内容占满整宽。删除失效的 `_railBottomInset` 与 `_bottomCopyRailInset`。验证：`flutter analyze` 0 issue、`flutter test` 287 用例全绿；雷电模拟器（720×1280 @320dpi）播放页截图确认。

[2026-10-19] android-flutter/ui - 修复 Android 12+ 启动页图标黑边（提交 `fcc84ef`）：黑边不是图片问题——Android 12+ 用 SplashScreen API 替代 `windowBackground`，未显式配置时系统直接把 `android:icon` 当作启动图标，而 launcher 图标是 adaptive icon、其 background 层为深色画布 `#0B0D11`，于是浅色启动背景中央出现黑方块包住紫徽章。新增 `drawable/splash_icon.xml`（只有徽章与播放三角、无背景层的矢量图，几何按系统 2/3 圆形裁切范围收在 108dp 视口内 60dp 方框里）与 `values-v31/styles.xml`、`values-night-v31/styles.xml`（显式设置 `windowSplashScreenBackground` / `windowSplashScreenAnimatedIcon`，浅深两色各用对应画布）；API < 31 仍走原 `launch_background` 纯色窗口不受影响。验证：带签名构建通过（aapt2 接受新资源），雷电模拟器（Android 14 / API 34）浅色与深色两种模式下启动页均为纯紫徽章、无黑边。

[2026-10-19] android-flutter/ui - 海报卡改用集数徽标，废弃右侧刻度尺（提交 `6d7bb9e`）：`PosterCard` 右下角的 `EpisodeSpine.mini` 在 136dp 卡片里每格不足一个物理像素，视觉上像渲染瑕疵而非信息，删除连同 `_maxSpineTicks` 窗口逻辑；版式改为「右上集数徽标 / 左下标题」两行，二者不再争同一行宽度，标题仍受 `posterOverlayTitleMaxLines` 限制。徽标为 88% 黑底 + 细白边胶囊（首版 70% 黑会在自带花体标题的浅色海报上透出底字，真机截图确认后加深），**不跟随系统字号缩放**（3.2× 下放大后会被省略成 `38 e…` 而失去意义，完整集数仍由卡片合并语义标签播报）。新增 `AppStrings.posterEpisodeUnitSingular`（`" ep"`）使单集显示 `1 ep` 而非 `1 eps`，按既有流程先进 Kotlin 源再生成、未手改生成物，`EXPECTED_FIELD_COUNT` 189 → 190；搜索结果横排行本就打印集数，传 `showEpisodeBadge: false` 避免一行出现两次。连带修复 3.2× 字号下底部导航溢出：`_AppNavigationBar` 原为固定 64px 高，大字号标签把 `Column` 撑出边界（四个 tab 全部 `BOTTOM OVERFLOWED`），改为 `minHeight: 64` + `IntrinsicHeight`，正常字号高度不变。验证：`flutter analyze` 0 issue、`flutter test` 287 用例全绿；真机雷电模拟器复核首页、Me 页继续观看、3.2× 与恢复 1.0× 四组截图；`design-system.md` 同步。

[2026-10-19] android-flutter/ui - 重做 launcher 图标为实心播放徽章（提交 `d6526da`）：原「竖版画框 + 竖排集数轨 + 播放三角」在 32dp 下三组细笔画全部糊在一起、实际启动器上不可辨认，改为单一实心标记——前景 68dp 紫色圆角方徽章（`#6C4DFF`，`AppColors.accentPrimary`）+ 白色播放三角（右移 3dp 做视觉居中，几何会左倾），几何全部落在 66dp 安全区内；背景深色画布 `#0B0D11` + 五层 5% alpha 同心圆模拟紫色光晕（VectorDrawable 无径向渐变原语）；新增 Android 13+ 单色层 `ic_launcher_monochrome.xml`（`evenOdd` 把播放三角从徽章挖空），两个 `mipmap-anydpi-v26` 图标补 `<monochrome>` 引用；新增 `tool/icon-preview.html` 纯 HTML/SVG 预览（24–144dp 尺寸阶梯、圆形/方圆/圆角方遮罩、亮暗壁纸、主题图标）。验证：`flutter analyze` 0 issue、`flutter test` 285 用例全绿；带本地签名构建 `app-debug.apk` 成功（aapt2 接受全部矢量资源）；装雷电模拟器（720×1280 @320dpi）后启动器实测清晰、放大确认边缘无模糊。`parity-matrix.md` D10 同步。

[2026-10-19] android-flutter - 代码审查（high）与简化（提交 `229ec4a`）：**审查发现 5 项，修复 4 项**。① 新契约测试实际只校验 15/189 个字段（10 个手挑切片 + 5 个保留字段），其余 174 个 Dart 值漂移不会被发现——生成器新增 `copyValues()` 输出全字段映射，测试改为全表比对，并用变异测试验证（把 `fundsLoginRequired` 改成任意值，测试立即报 `AppStrings.fundsLoginRequired drifted from the Kotlin golden`，旧切片写法不会报）；② `docs/toolchain.md`、`tool/port_app_strings.py` 及生成物 banner 仍引用不存在的 `test/core/app_strings_contract_test.dart`（`74f9795` 只改了 Markdown、漏了生成器模板，下次重新生成会把错误声明写回），三处一并改为 `generated_contract_test.dart`；③ `AppUiCopy` 的 `copy`/`retry` 两个常量零引用、与「仅 `seeAll` 未使用」的声明矛盾，删除（39 → 37）；④ `_ResultsSection` 的 `hasResults` 恒等于 `results.isNotEmpty`，属派生状态，删除参数并合并两个空分支。**未修复（记录在案）**：切换 Tab 会销毁 `SearchScreen` 的 State（`RootTabSwitcher` 的 `AnimatedSwitcher` 按 destination 换 key），搜索进行中切走再切回会丢失 `_awaitingResults`、重新出现一次跳变；修复需把 pending query 提升到状态层，会改变 `AppUiState` 契约，超出本次范围。验证：`flutter analyze` 0 issue、`flutter test` 285 用例全绿、生成器连跑两次只新增 `copyValues` 块且 golden 不变（幂等）。

[2026-10-19] android-flutter/ui - 完成 UI 重设计 Phase 5-3（Discover 搜索骨架重排，提交 `cd22106`，3 文件 +202/−32；记录见 `android-flutter/docs/ui-redesign-notes.md` §11）。**问题**：`AppStateController.search()` 只在响应落地后才写 `searchQuery`，因此首次搜索期间 `searchShowsResultsFirst('', 0)` 为 false——屏幕先渲染发现分组 + 起始提示，响应回来再整块换成结果区，产生一次明显跳变；同时 `state.isLoading` 是全局标志，登录/账户刷新等别处加载也会让 Discover 挂出搜索骨架。**改法**：`SearchScreen` 由 `StatelessWidget` 改为 `StatefulWidget`，持有 `_submittedQuery`（本屏刚提交、状态层尚未回显的 query）与 `_awaitingResults`（这轮加载是否为本屏发起）；`resultsFirst` 改由 `displayQuery` 判定，仅当「本屏发起 + 仍在加载 + 状态层尚无结果」时采用 pending query，因此第二次搜索时标题仍对应屏幕上那批结果，标题与列表不会互相矛盾。**失败语义**：状态层失败时不写 `searchQuery`，屏幕随请求结束一并丢弃 pending query，回到发现分组与起始提示，而不是错误地声称「没有结果」（失败文案由 shell 消息条负责）。**连带简化**：删除发现分组下段的 `isLoading` 骨架分支（该位置现在只可能在无提交 query 且无结果时到达），`_ResultsSection` 改收 `hasResults`/`results`/`awaitingResults` 显式参数，不再读全局 `state`。**验证**：`flutter analyze` 0 issue、`flutter test` 全量 **285 用例全绿**（`search_screen_test.dart` 18 → 19）；两条既有用例改走真实 UI 提交路径（直接调 `controller.search()` 会绕开本屏状态、测的是 UI 不会走的分支），新增「搜索失败回到发现分组」用例，并修正原本不成立的 `find.text('Results')`/`find.text('Popular lanes')` 断言——`AppSectionHeader` 会把标题转成大写，改用 `widgetList<AppSectionHeader>` 比对原始 title。**已知剩余项**：未做真机复核（本机设备未连接）；`searchEpisodeCountSuffix` 的 `' eps'` 硬编码、详情页接线、Phase 4/5 发版仍未结项（`pubspec.yaml` 仍为 `0.9.2+44`）。

[2026-10-18] android-flutter/ui - 完成 UI 重设计 Phase 5-2（孤儿文案清理 + 补上失效的契约验收，提交 `74f9795`，6 文件 +271/−290；记录见 `android-flutter/docs/ui-redesign-notes.md` §10）。**Kotlin 源减字段**：`android-app/.../ui/format/AppStrings.kt` 删除 **27 个**「Kotlin 与 Dart 两端都零引用」的字段（`languageTitle`/`languageSubtitle`、`diagnostics*` 7 个、`accountTransfer*` 9 个、`accountViewAction`、`accountOrdersRecentSuffix`、`accountSelectedLanguage`、`accountPointsPillPrefix`、`accountLoggedInRewardHint`、`authCaptchaLabel`、`playerCommentUnit`、`accountWalletUnbindCode`、`vipDialogTitle`、`accountWithdrawRateLabel`、`commonNotSupported`、`bookRowViewAction`），diff 为 **0 新增行 / 54 删除行**；生成器 `tool/port_app_strings.py` 的 `EXPECTED_FIELD_COUNT` 216 → **189**，`lib/core/formatting/app_strings.dart` 与 `test/core/app_strings_golden.json` 由生成器重新产出、未手改。保留 5 个「Kotlin 零引用但 Dart 在用」的字段（`accountWalletAddressLabel`、`accountVipStatusActive`/`Inactive`、`accountVipExpiryPrefix`、`bookRowFallbackSuffix`）。**Dart 侧**：`lib/core/formatting/app_ui_copy.dart` 删除 **38 个零引用成员**（35 常量 + 3 个 `static String` 方法，77 → **39**），仅 `seeAll` 保留（lib 零引用但 `design_system_test` 在用）。**上一轮验收声明是失效的**：`parity-matrix.md` 曾声称存在 `test/core/app_strings_contract_test.dart` 与 `test/state/app_ui_state_contract_test.dart` 逐项匹配 golden，但这两个文件在任何提交里都不存在、全仓也没有任何测试读 `app_strings_golden.json`/`app_ui_state_golden.json`，golden 一直是**无消费者的快照**；现新增 `test/core/generated_contract_test.dart`（9 条）真正消费两个 golden（AppStrings 读写切片、5 个保留字段、discovery 分组含 tag 顺序、AppUiState 47 字段 / 4 枚举的结构与字面量默认值、枚举成员顺序、集合默认空）。**验证**：只删不改（golden 216 → 189 键、新增键 0、189 个保留值逐字节不变、键顺序保持，被删字段在 HEAD 的 Kotlin 源码零引用）；生成物纯度（磁盘文件与生成器输出 LF 归一化 SHA-256 相同，连跑两次幂等）；Kotlin `:app:compileDebugKotlin`/`:app:compileDebugUnitTestKotlin` 用 `--rerun-tasks` 真编译通过并真跑 `:app:testDebugUnitTest` **119 用例 0 失败**（含 `VisualTextContractTest`）；Dart `flutter analyze` 0 issue、`flutter test` 全量 **284 用例全绿**（275 + 新增 9 条）；verifier 独立复核三处计数 189、抽 10 字段 Dart==Kotlin==golden 并全量比对 189/189 三方一致（mismatched=0）、在用常量未误删——增量缺陷 0。**已知剩余项**：7 项「Kotlin 侧仍在用、Dart 侧已不用」的字段（`playerCommentsEmpty`、`vipActiveLabel`/`vipExpiryLabel`、`accountWithdrawalRecordsEmpty`、`accountGuestSignInSubtitle`/`accountGuestRegisterSubtitle`、`accountOrdersTitle` 与 `vipRecordsTitle` 重名、`searchEpisodeCountSuffix` 的 `' eps'` 硬编码来源）要删必须连 Kotlin UI 一起改；做详情页 / 加载失败重试 / 复制反馈时需重新往 `AppUiCopy` 加回常量（非生成物）；`.worktrees/` 下旧 worktree 各自带 216 版 `AppStrings.kt` 与 golden，从那里构建会拿到旧值。

[2026-10-17] android-flutter/ui - 完成 Phase 5-1（组件收敛 + 错误透传治理，提交 `9eb6a03`，12 文件 +597/−169；记录见 `android-flutter/docs/ui-redesign-notes.md` §9）。**组件收敛**：`AppSectionHeader` 新增公开常量 `sectionHeaderTrailingWidthFactor = 0.5`，并排分支给 trailing `ConstrainedBox(宽 × 0.5)`、堆叠分支给全宽有界槽（此前 trailing 在两种分支都无柔性，Discover 计数胶囊与提现空态只能由调用方各自打补丁），并删除 `search_screen.dart` 里的 `LayoutBuilder + 0.45 系数` 调用方补丁；`AppAmountBlock` 的 `unit` 槽改 `Flexible + 单行省略`（320@3.2× 真省略而非溢出）；`AppListRow` 新增可选 `subtitleWidget`（默认 null 行为逐字不变，标题与 `subtitle` 仍由行节点统一播报，`subtitleWidget` 不被排除以保留自身语义节点与长按复制），Me 页客服行改回共享 `AppListRow(subtitleWidget: SelectableText(...))`，删除私有 `_SupportRow`（约 75 行）。**错误透传治理**（真机曾把 Cloudflare 525 整段 JSON 直接显示在消息条）：`api_client_exception.dart` 新增共享纯函数 `sanitizeUpstreamMessage()`（首字符 `{`/`[`/`<`、长度 > 200、含控制字符 → 空）、`looksStructuredMessage()`、`maxUserFacingMessageLength = 200`，`ApiClientException.message` 契约收紧为「构造时已净化，空串表示 body 不可作消息」；`http_reel_short_api_client.dart` 对信封 message 与裸 body 统一净化、不可用则空消息但保留 statusCode/code（不再回退 reasonPhrase）；`app_state_controller.dart` 的 `_userFacingErrorMessage` 净化后为空时 **5xx 回退 `contentLoadFailed`、其它回退 `requestFailed`**（均为既有文案，零新增可见文案），分类判断仍读原始 message、位于净化之前，401/403/409 既有分支不受影响。**验证**：`flutter analyze` 0 issue、`flutter test` 全量 **275 用例全绿**（新建 `test/error_message_sanitization_test.dart` 12 条覆盖 Cloudflare 525 JSON / HTML 502 / 4000 字符纯文本 / `{code,message}` 信封 / 短纯文本 / 空 body 六种输入 + 纯函数规则 + 状态层回退，并断言消息条不含 `{`/`<`/`ray_id`/`cloudflare`；`design_system_test` 新增 4 条组件用例与矩阵项，`account_screen_test` 1 处适配，两护栏测试零改动）；verifier 独立复核（真实 HTTP 客户端 + stub 传输层实测六种输入、401/403/409 逐条确认零影响、4000px trailing 实测并排 180.0/堆叠 320.0、unit 真省略、`subtitleWidget` 语义契约、并用 git 历史核实「提现屏从未有过 `_SupportRow`」——先前笔记记录有误已更正）增量缺陷 0。**已知剩余项**：`AppSectionHeader` 的 trailing 封顶 0.5 为启发式值（若将来需要更大占比应参数化）；VIP 两处金额仍是 `value: '<n> USDT'` 的绕开写法，回填 unit 槽需同步改 `vip_recharge_screen_test.dart:237` 的逐字断言（留到下一批）；提现屏钱包行是「图标 + network · address」的 `Row` 而非可选中文本行，未套 `AppListRow`；净化只作用于消息条文案，`ApiHealthStatus.service` 也会收敛成共享文案（`kDebugMode` 仍打印截断原始 body）；Phase 4/5 改动尚未发版（`pubspec.yaml` 仍为 `0.9.2+44`）。

[2026-10-17] android-flutter/ui - 完成 UI 重设计 Phase 4 第五屏（VIP 充值 + 提现子页重设计 + 新增共享 `AppStatusPill`，提交 `2706269`，7 文件 +1782/−379；规格与结果见 `android-flutter/docs/ui-redesign-notes.md` §7.10/§7.11）。两屏已在 Phase 3 套上 `AppSubPage` 外壳，本轮只重排内容（屏内无自绘 header）：**VIP 充值** `AppMembershipChip` + 到期 caption 头部卡（快照未知渲染中性骨架）→ 唯一 `AppSectionHeader(vipPayTitle)` → 按状态一张 `AppSectionCard`（create：价格 `AppAmountBlock` + 状态 pill + Unlock primary；pending：金额块 + 订单号/有效期 `AppLedgerRow` + 地址 `AppListRow` 单行省略 + Copy + **白底 quiet-zone QR** + `numeric` 倒计时 + refresh secondary；confirmed：success pill + check + refresh；guest：登录 + Sign in）；**提现** 可提现积分 `AppAmountBlock` + Min/汇率 caption → 表单卡（digits-only `AppTextField` + Estimated/Fee 行 + 钱包行 + Submit primary）→ 确认卡（`AppLedgerRow` 金额 + Confirm primary / **Edit amount** secondary）→ 历史区（`AppSectionHeader` + 单卡多行：金额 + 状态 pill + `network · address · createdAt` 单行省略 + 详情行 + `withdrawalRecordsEmptyTitle` 空态）；订单决策树、两步流程、三重闸门、`submitWithdrawal` 调用与状态映射全部未动。**新增共享组件** `lib/ui/components/app_status_pill.dart`（`AppStatusPill` + `AppStatusTone{success,warning,danger,neutral}` → accent + 12% tint 胶囊 + 图标 + 单行省略 + **内部柔性包装** + 语义单节点无 tap），VIP 三处与提现两处调用点改用它，两屏私有 `_StatusPill` 全部删除；`AppUiCopy` 仅追加 `statusExpired = 'Expired'`。**本轮抓到并修掉 5 个缺陷**：① VIP 头部 chip 在 320@3.2× 溢出 269px（→ Flexible）；② 提现历史状态 pill 同类溢出（→ Flexible）；③ `AppAmountBlock` 4 字符 unit 在 3.2× 溢出（→ 单位折进 value）；④ **深色主题下二维码不可扫描**（模块色取面牌前景，暗色主题变浅色模块 → 改主题无关白底 quiet zone + 深色模块，零裸色值）；⑤ **VIP 状态 pill 在常见尺寸下溢出**（verifier 抓到、写作者首轮遗漏：320@3.2× 下 confirmed 101px / rejected 63px / vipActive 629px、**360@1× 下 expired 214px** → 抽共享 `AppStatusPill` 并把 expired 整句拆成「短词 pill + caption」）。**验证**：`flutter analyze` 0 issue、`flutter test` 全量 **259 用例全绿**（新建 VIP 21 条 + 提现 13 条测试，design_system_test 为新组件新增 3 条用例与 'status pills' 矩阵项、既有断言未改，六套护栏测试零改动）；verifier 两轮独立复核（第一轮抓到 pill 溢出与 'VIP active' 三处重复，第二轮复跑四个修复前复现点全部 clean 且用 `RenderParagraph.didExceedMaxLines` 确认是**省略而非裁切**、四配置 × 八状态 32 组矩阵零异常）增量缺陷 0。**真机**：雷电模拟器（Android 14 / 360×640dp，登录态）改造前 `60-vip-before.png` / `62-withdrawal-before.png`、改造后 `65-vip-final.png` / `66-withdrawal-final.png`。**已知剩余项**：`statusExpired` 是新增可见短词常量（`AppUiCopy`，非 AppStrings，整句仍作 caption 显示）；提现「手续费/预计到账」仍是 `withdrawalConversionLines()` 组合文本行而非 `AppLedgerRow` 键值对（拆开需改共享格式化器与 AppStrings）；提现钱包缺失告警仍是长句（320@3.2× 会省略）；VIP 状态两串与 `accountWithdrawalRecordsEmpty` 因改用共享语言成为孤儿文案；`AppAmountBlock` 的 unit 仍非柔性（建议 Phase 5 统一加 Flexible）。

[2026-10-17] android-flutter/ui - 完成 UI 重设计 Phase 4 第四屏（Discover 发现/搜索页逐屏重设计，提交 `10035cf`，5 文件 +957/−147；规格与结果见 `android-flutter/docs/ui-redesign-notes.md` §7.8/§7.9）。**结构**：新增 `screenTitle` 页标题「Discover」与整页 `pagePadding`（原来以搜索框直接开场）；搜索区保留 `AppTextField`（重建不清空未提交输入的既有 `didUpdateWidget` 语义）+ primary「Search」（护栏依赖的可见文本保留）；发现分组改为**两级层次**——唯一 `AppSectionHeader(searchTagsTitle)`（POPULAR LANES）+ 每组句首大写次级标签（`caption`/`textSecondary`/w600、无 accent 装饰），chips 令牌化（surfaceAlt/hairline/pill/minHeight 44/宽度封顶单行省略）；结果区 `AppSectionHeader(searchResultsSection)` + `N dramas` 计数胶囊 + query 单行省略 + 令牌化结果行（88×132 海报 + 2 行标题 + 集数 + 可选简介，**单一合并语义节点**，修掉原来海报/标题重复播报）；首次搜索飞行中显示与结果行同形的骨架；初始/无结果分别用 `searchStart*`/`searchNoResults*` + 非交互 'Search again' pill。**历史缺陷结案**：§3 台账挂账已久的「Discover 在 320px + 3.2× 下横向溢出」由裸 `Row`（组标题无 Flexible）改为 `AppSectionHeader` 的窄屏/大字号堆叠分支**结构性消除**，verifier 用「四种尺寸逐组滚动零异常 + 各级 header 子树内 `ClipRect` 零命中」取证不是 clip 掩盖。**本轮抓到的两个缺陷**：① 结果区计数胶囊在 320×640@3.2× 溢出 51px（`AppSectionHeader` 堆叠分支 trailing 无 Flexible）→ `LayoutBuilder` 封顶 `内容宽 × 0.45` + 省略号；② 初始态出现两个连着的同款大写分区头（POPULAR LANES 紧接 STORY MOODS，真机截图发现）→ 分区头唯一 + 组标题降级；另给 `PosterCard` 增加 `showTitleOverlay`（默认 true，既有 6 处调用零改动；false 时几何/mini spine/语义 label 全部不变），搜索结果行传 false 消除海报叠标题与行内标题的视觉重复。**验证**：`flutter analyze` 0 issue、`flutter test` 全量 **226 用例全绿**（新增 `test/search_screen_test.dart` 18 条 + `design_system_test` 的 PosterCard 新参数用例，护栏 ui_wiring / navigation_shell 零改动）；verifier 两轮独立复核（历史溢出结构性消除、行为 parity 逐条、空态/加载态形状、单一合并语义节点、令牌合规、320/360/1440 × 减少动效、`showTitleOverlay` 调用点清查与默认分支零变化）增量缺陷 0。**真机**：雷电模拟器（Android 14 / 360×640dp，登录态）改造前 `50/51/52-discover-before-*`、改造后 `57-discover-final.png`（两级分区层次）与 `59-discover-results-final.png`（RESULTS + 42 dramas + 干净海报 + 单行标题）。**已知剩余项**：加载骨架同时挂在结果区与发现区下段两个分支（状态层搜索成功前不写 `searchQuery`，是 Kotlin parity 的直接结果，Phase 5 可用「已提交 query」本地状态优化重排）；`' eps'` 仍是既有硬编码英文（HEAD 已存在，需改 Kotlin 源新增 `AppStrings` 字段）；搜索历史未接（`searchRecentSection`/`searchRecentClear` 已存在）；lane 之间仅用间距分隔。

[2026-10-16] android-flutter/ui - 完成 UI 重设计 Phase 4 第三屏（播放器 Player 逐屏重设计，提交 `869f607`，3 文件 +1298/−455；规格与结果见 `android-flutter/docs/ui-redesign-notes.md` §7.6/§7.7）。**表现层重写**（手势/阈值/采样/领奖/VIP 门控/重试逻辑一行未改，verifier 用 git diff 证明）：全部媒体 chrome 改走 `AppMedia` 令牌（`onMedia`/`onMediaSecondary`/`glass`/`glassStrong`/`glassDanger`/`divider`/`scrim`/`gradientBase`/`bottomCopyGradient`），`player_screen.dart` 内 `Colors.*` 与 `Color(0x` **零命中**；顶栏返回 40 → **44** 且语义由屏名 'Player' 改为 `AppUiCopy.back`；奖励圆环几何与显示门控不变、加 48px 玻璃底板 + 圆心数字走 `onMedia`（静默态弧色 `onMediaSecondary`）；右侧动作栏三项统一 44px 玻璃底板 + 选中态「实心图标 + 强调色」双通道、语义标签补全为 `playerLike/playerComments/playerFavorite`（原来 like/comments 只播报裸数字）；底部信息区改 `bottomCopyGradient` + 令牌化进度条 + 整宽媒体面按钮 `Episodes · EP n`（`_MediaButton`：glass + divider + `onMedia` + ≥44）；**选集抽屉从等高网格换成 `EpisodeSpine.full`**（每 tick 44×44、相邻不重叠、已看/进行中/当前/门锁四态，点当前集只关、点其它集切换、锁集走付费层的既有行为不变）；评论抽屉套 `AppSheet` + `AppEmptyState`/`AppInlineLoading`；错误层三动作改 `AppButton`（Retry primary / Next secondary / Back tertiary，primary 恰 1 个）并改纵向堆叠；`AppUiCopy` 仅追加无障碍语义常量 `playerCommentSend`。**本轮抓到的三个缺陷**：① 错误层动作 Row 在 360dp 下 `RenderFlex` 溢出 75px；② **底部选集入口在视频上不可读**——`AppButton(secondary)` 的 `textPrimary` 前景压在媒体面（浅色主题实测 1.05–1.12:1）→ 换成令牌化媒体面按钮（16–19:1）；③ 全文件「媒体面用面牌前景」自查又修三处：奖励失败横幅（2.95:1）、底部集标题（4.14:1）、**奖励圆环圆心数字（中灰视频 1.54:1）**，并把错误卡整体改回 `colors.surface` 平面使四个按钮变体回到设计时的平面（verifier 自算 WCAG：浅/深两主题六个配对 5.07–18.10 全部 ≥4.5）。**验证**：`flutter analyze` 0 issue、`flutter test` 全量 **207 用例全绿**（新增 `test/player_screen_test.dart` 23 条，护栏 ui_wiring / navigation_shell 零改动）；verifier 两轮独立复核能力包括自建假 `VideoPlayerPlatform` 端到端验证单击暂停/双击点赞（不误触单击）、`EpisodeSpine.full` 6 tick 实测 44×44 且相邻交集为空、自实现 WCAG 公式复核对比度，增量缺陷 0。**真机**：雷电模拟器（Android 14 / 360×640dp，登录态）改造前 `30/31-player-*-before.png`、改造后 `43/44-player-*-final.png`，并真机复核三条竖滑手势（左 1/4 → 41% 亮度胶囊、右 1/4 → 100% 音量胶囊、中间竖滑 EP 05 → EP 06，证据 `38–42-*.png`）。**已知剩余项**：评论空态改用 `AppUiCopy` 两段式后生成串 `playerCommentsEmpty` 成为孤儿文案；底部选集入口与 `playerEpisodeSelectorPrefix` 现在有两个 'Episodes' 来源；点赞强调色由裸值粉 `#FF5C7A` 改为 `accentDanger`（语义略过载，若要保粉色应新增 `accentLike` 令牌）；动作栏选中色 3.46–4.14:1 属「图形达标、文字未达 4.5」，靠形状通道兜底；奖励说明弹层仍为本地 `showModalBottomSheet` + 硬编码内边距（surface 平面、令牌合规）留 Phase 5 套 `AppSheet`；三项 widget 层未取证、建议真机复核——Retry 重建播放器、奖励失败横幅文字色的运行时证据、圆环 `onMediaSecondary` 静默分支。

[2026-10-16] android-flutter/ui - 完成 UI 重设计 Phase 4 第二屏（Funds 资金中心逐屏重设计，提交 `0f25cb4`，6 文件 +1215/−517；规格与结果见 `android-flutter/docs/ui-redesign-notes.md` §7.4/§7.5）。**结构**：`funds_screen.dart` 从「手写 `_Panel`/`_FundsActionTile`/`_LedgerRecordRow`/`_VipOrderCard` + 四个私有 spacing 常量 + 16px `titleLarge` 标题」改为——`screenTitle` 22px 页标题 + `pagePadding` → 余额 hero（VIP `premiumSurface` + `accentPrimary`/`strokeEmphasis` 描边 + 状态 chip + 到期 caption；**可用积分作主数字** `AppAmountBlock(hero)` + `pts`，Total/Credited/Frozen 退为 `AppLedgerRow`、冻结 > 0 用 `accentWarning`；**快照未知渲染中性骨架占位**，不显示 0、不显示 VIP 文案；游客态零指标 + 主 CTA）→ `ACTIONS`（两列 `AppButton`：Withdraw primary / VIP recharge secondary + caption）→ `POINT RECORDS` / `VIP ORDERS` / legacy `ORDERS` 三个 `AppSectionCard` 分区（空态走 `AppEmptyState` + `AppUiCopy`，订单状态映射与金额文案不变）→ 加载态换成与到达内容同形的骨架，不再用通用 `AppSkeletonList`；零裸色值、零内联硬编码间距。新增 `test/funds_screen_test.dart` 16 条。**新增共享组件** `lib/ui/components/app_membership_chip.dart`（`AppMembershipChip` active/inactive 两态 + `membershipChipPlaceholderSize` 112×22）：Me 与 Funds 改引用，删除两处私有 `_MembershipChip`，并消除 `funds_screen.dart` → `account_screen.dart` 的**屏幕 import 屏幕**依赖（校验 `lib/ui/screens` 内 screen→screen import 零命中）；抽取零差异（Me hero 高度差逐位复现 1× +2.0 / 2× −7.0 / 3.2× −16.4，|Δ|≤40）。**本轮抓到的两个缺陷**：① hero 的 chip+到期行在 3.2× 下 `RenderFlex` 溢出 169px（非弹性子项被无界布局撑破）→ 两侧改 `Flexible`；② **ACTIONS 两个按钮都是实心 primary**（`AppButton.variant` 默认 primary，其文档写明「exactly one per screen region」）→ `_ActionTile` 加 `variant` 参数后 Withdraw primary / VIP recharge secondary（这条是「真机截图 + 代码核对」才发现，widget 行为断言不覆盖变体选择，已作为后续屏幕检查项）。**验证**：`flutter analyze` 0 issue、`flutter test` 全量 **188 用例全绿**；verifier 两轮独立复核（行为 parity、hero 四态、加载态形状、令牌合规、§7.2 语义、320@3.2×/360/1440 × 减少动效两态、按钮变体表、chip 零差异、跨屏依赖 grep）增量缺陷 0。**真机**：雷电模拟器（Android 14 / 360×640dp，登录态）改造前基线 `artifacts/ui-review/ldplayer/14-16-funds-before-*`、改造后 `24-funds-final-top.png`。**已知剩余项**：legacy `ORDERS` 与 `VIP ORDERS` 分区标题文案相同（`accountOrdersTitle`/`vipRecordsTitle` 均为 `'VIP Orders'`，既有生成文案，建议 Phase 5 改 Kotlin 源后重新生成，不要加临时硬编码）；`state.isLoading` 仍是全局标志；骨架块尺寸为文档化命名常量（Phase 5 可提成骨架令牌）；本屏未在真机上验证 TalkBack 实听与 3.2× 换行观感。

[2026-10-16] android-flutter/ui - 完成 UI 重设计 Phase 4 第一屏（Me 账户页逐屏重设计，提交 `b5646fc`，4 文件 +1675/−472；规格与结果见 `android-flutter/docs/ui-redesign-notes.md` §7）。**结构**：`account_screen.dart` 从「全手写 `Container` 卡片」改为设计系统组件与令牌——`screenTitle` 页标题（22px，原 26px `displayLarge`）+ `appSpacingOf(context).pagePadding` → 身份 hero（VIP 用 `premiumSurface` 底 + `accentPrimary` + `strokeEmphasis` 描边 + 56px 字母牌 + 状态 chip + 有效期 caption；非 VIP 用 `surface` + `hairline`；游客态同 hero 内两个整宽 CTA「Sign in / Create account」）→ `SHORTCUTS` 分区卡（3 行 `AppListRow`，取代 3 张全宽大卡）→ `CONTINUE WATCHING`（`size/limit` pill + `PosterCard` 轨道）→ `SETTINGS` 分区卡（钱包行 / 银行卡只读行 / 客服邮箱行 / 版本+Check）→ destructive 退出登录（原 tertiary）。新增行零裸色值、零硬编码间距，行为与文案零变化（4 个弹层、复制邮箱、检查更新、退出登录、游客可见性规则、`accountContinueWatchingLimit()` 的 size/limit 语义全部保持），`AppStrings` 生成物未改。**`AppListRow`（共享组件）**：① 语义契约修复（notes §7.2）——每行 `container: true` 自成节点、说明文字 `ExcludeSemantics` 只播报一次、`InkWell(excludeFromSemantics: true)` 防重复节点、`trailing` 控件保留自身可读可点节点、不可点行不暴露 tap，修掉「可点行与不可点行同装进一张 `AppSectionCard` 时整卡塌成一个语义节点、客服邮箱与 Check 在 TalkBack 下彻底消失」的中等无障碍缺陷（`design-system.md` §7.4 同步补「Trailing controls are never swallowed」）；② 新增 `subtitleMaxLines`（默认 null）与 `trailingTakesIntrinsicWidth`（默认 false；开启后 trailing 取固有宽度、capped at 行宽/2、`textScaler > 1.6` 自动回退 1:1 分摊），供标签型动作不折行。**修复的真机/验证发现**：hero VIP 未知态误报（快照未到达时先渲染中性 `AppSkeleton` 占位且随 `textScaler` 缩放，hero 高度差从 3.2× 下 +166px 收到 −16.4px，1× 仅 +2px）、游客 monogram 由 `?` 改为 `G`、钱包地址 6 行折行改为单行省略、钱包动作 `Replace wa…` 截断改为完整渲染（真 Roboto 下 95.67px，360/320dp 均不裁）、客服邮箱断词折行改为整行单行（`SelectableText` + `maxLines: 1`，屏幕内私有 `_SupportRow`，硬约束：出现第二个消费者时必须抽 `AppListRow.subtitleWidget`，不得第三次复制）。**验证**：`flutter analyze` 0 issue、`flutter test` 全量 **173 用例全绿**（新增 `test/account_screen_test.dart` 22 条，`design_system_test` 40 → 42，两条护栏测试零改动）；verifier 四轮对抗式复核（含把真实 Roboto 载入测试引擎做设备量级宽度测算，实测测试字体约为 Roboto 的 2.1 倍宽）增量缺陷 0。**真机**：登录态截图来自小米真机 `c4e4da7e`（`artifacts/ui-review/phase4/`），游客态与交互改在**雷电模拟器**（Android 14 / 720×1280 @320dpi = 360×640dp，`artifacts/ui-review/ldplayer/`）验证——guest hero、模态认证面板（含密码显隐）、Funds 游客页、Android 14 预测性返回（面板先关、Funds→Me 落点一致）、首页骨架加载态；注意雷电的 adb 5555 端口会被腾讯 Androws 占用导致 `device offline`。**已知剩余项**：Phase 4 其余屏（Funds/Player/Search/Detail）未重设计，Funds 仍是 `titleLarge` 自绘页面；四个弹层内部还有 16 处历史硬编码间距；`SelectableText` 在极端字号下是裁剪而非省略号；3.2× 下 VIP chip 文案视觉省略（语义完整播报）。

[2026-10-16] android-flutter/ui - 完成 UI 重设计的 Phase 2 收尾与 Phase 3（提交 `bc33093`，19 文件 +3689/−537，只动 `android-flutter/lib` 与 `android-flutter/test`；计划见 `docs/plans/2026-10-16-ui-redesign-phase2-phase3.md`，现场记录见 `android-flutter/docs/ui-redesign-notes.md`）。**Phase 2 收尾**：`AppSkeleton` 新增文本（末行 0.6 宽）/圆形/自适应海报网格三个变体（共用同一 shimmer 与减少动效平板）；`AppEmptyState` 把 pill 契约显式化为纯函数（状态不是控件、无 tap 语义与焦点、单节点播报，`onAction` 非空但标签空白时不得渲染死按钮）；`AppTextField` 内置密码显隐切换（≥44×44、语义与 tooltip 走 `AppUiCopy`、`canRequestFocus:false` 不打断 `next` 遍历、不清空文本与光标）与点击字段外部收键盘（移动端/桌面端一致，可关闭）；`EpisodeSpine.full` 交互 tick 提升到 44×44 命中区（行距同步、视觉几何不变、相邻不重叠，新增 `episodeSpineMinTargetExtent`），`describe()` 的 in-progress 文案改走 `AppUiCopy`；`AppMessageBar` 语调词与关闭 tooltip 改走 `AppUiCopy`、关闭按钮命中区 ≥44×44。**Phase 3**：新增 `lib/ui/routing/app_route_stack.dart` 纯函数契约（`buildRouteStack`/`resolveBackOutcome`/`shellExitForPoppedDestination`/`shellShowsNavigationBar`），`AppShell` 的 body 改为 `Navigator(pages: buildRouteStack(state))`——一级 Tab 是唯一 root page（切 Tab 为 in-place update + 220 ms 交叉淡入），favorites/withdrawal/vipRecharge/player/detail 是真 pushed page 并吃到主题的 reel-change 转场（player 保留上滑+淡入），`onDidRemovePage` 派发前按**当前**状态复核（框架只在命令式移除时回调，状态驱动收缩不回调）；新增 `AppSubPage` 全屏子页外壳（`screenTitle` 唯一标题角色、≥44px 返回触达、安全区与 `pageBottomInset` 预留、viewInsets 兜底），withdrawal/vipRecharge/favorites 去自绘 header/Scaffold/SafeArea/私有 padding；认证面板与 VIP 付费层改为 root navigator 模态路由（scrim/焦点陷阱/`scopesRoute`/系统返回来自 route，`PopScope(canPop: !busy)` 保留 busy 门禁），全局消息条宿主从 shell 上移到 `MaterialApp.builder`（root Navigator 之上、自带对输入透明的 Overlay），恢复 Kotlin z-index 2 语义；返回优先级固定为「认证面板 > VIP 付费层 > pushed page > FUNDS→ACCOUNT > 退出」。**验证**：`flutter analyze` 0 issue、`flutter test` 全量 **149 用例全绿**（新增 `routing_contract_test` 9 条、`navigation_shell_test` 17 条，`design_system_test` 扩张到 40 条）；verifier 三轮对抗式复核自写 90+ 条临时断言（全部删除）抓到并修复两个真实缺陷——`AppTextField` 的 `selectAllOnFocus` 监听器归属（use-after-dispose）与返回派发和同轮切 Tab 的 microtask 竞态，并纠正「`onDidRemovePage` 会因 `pages` 收缩回调」的错误认知。**真机 smoke**（`c4e4da7e`，1080×1920，Android 8.1，开发者选项关闭动画；证据在 gitignored 的 `artifacts/ui-review/phase3/`）：子页外壳与返回链 Withdrawal→Funds→Me、播放器返回来源、加载态骨架满宽可见均截图复核；期间用 Dart VM service + `ext.flutter.debugDumpApp` dump widget 树定位到 `AppSkeleton` 在 `width == null` 时 `SizedBox` 宽 0（Home/Favorites/Funds 三处加载态此前一直是纯空白）的既存缺陷并修复为填充传入约束（要求横向有界父容器）。**已知剩余项**：Phase 4 逐屏重设计未开始（详情页仍不可达、搜索页 320px+3.2× 横向溢出、Me/Funds 标题角色未收敛、三处 `AppSkeletonList` 未换成与内容同形的变体）；真机偶遇生产入口 `shortlink.hjj888.cc` 返回 Cloudflare 525 时消息条原样透传整段 Cloudflare JSON（既存行为，留 Phase 5 收口）；`scripts/android-ui-review.ps1` 的固定场景未整套跑（改用针对性 adb + 截图取证）。

[2026-10-16] android-app/release/scripts - APK 自动更新不再要求发布包带签名，改为核对 SHA-256 与文件大小。`scripts/publish-android.py` 移除 `apksigner` 签名/钉死证书校验（删除 `EXPECTED_RELEASE_CERT_SHA256`、`verify_expected_certificate`、`normalize_sha256_digest`，`verify_apk` → `verify_apk_metadata`，发布阶段只用 `aapt` 预检包名 `com.reelshort.app`、versionName、versionCode）；新增 `hash_file`（单遍流式读取，摘要与字节数同源）与 `verify_artifacts`，发布前核对摘要为 64 位小写十六进制、APK 磁盘大小与哈希时一致、`.sha256` 旁路文件 ≤1024 字节且匹配 App 下载器正则 `^([a-fA-F0-9]{64})(?:\s+\*?[^\r\n]+)?$` 且摘要与 APK 一致；旁路文件内容改用发布对象名（`<sha256>  ShortLink-v<版本>.apk`，可直接 `sha256sum -c`）；`upload_to_cos` 上传后新增 `verify_uploaded_size`，用 `head_object` 回核两个 COS 对象的 `Content-Length` 与本地一致，不一致或读不到即失败并提示重传；`apk_object_key`/`sha256_object_key` 抽成函数消除键名重复，并清理死代码 `run_quiet`/`run_capture` 与未使用的 `datetime` 导入。客户端（Flutter/Kotlin 两版）下载后本来就只核对清单 `sizeBytes` 与下载长度、以及 `.sha256` 摘要，不做签名校验，本轮未改客户端、后端 `app_releases` 结构与清单字段；Gradle 打包前的 fail-closed 正式证书门禁保持不变，所以发布包仍然是正式签名的，只是发布环节不再校验签名。测试 `scripts/tests/test_publish_android.py` 11 → 19 用例（`python -m pytest scripts/tests/test_publish_android.py` 19 passed）：签名校验移除（只调用 aapt、模块不再暴露证书校验函数、源码无 `--print-certs`）、旁路文件契约与 `release_downloader.dart` 的 `maxChecksumBytes`/正则逐字对齐、大小与摘要不一致/超大旁路文件/无摘要被拒、上传后 COS 大小不一致或 COS 不可达时报错、对象键稳定，并保留 Gradle 正式签名门禁的锁定断言。真实产物复核（不上传、不发布）：对 `android-flutter/build/app/outputs/flutter-apk/app-release.apk` 跑 metadata + 摘要 + 大小核对，得到 SHA-256 `2a9ab018a9c285a379b07e3a0fc42fa770df5e56cca84aab2a92e753b60a255e`、56,664,444 字节，与已发布的 0.9.2 记录一致；对 COS 上同名对象做只读 `head_object` 回核，APK 对象 56,664,444 字节一致，历史 `.sha256`（81 字节、内含旧文件名 `app-release.apk`）因与本地新旁路文件大小不同被正确拦下。文档同步：`docs/deploy/publish-android.md`、`docs/deploy/release-checklist.md`、`android-app/SIGNING.md`、`AGENTS.md`。**未执行**：真实发布（需上传 COS 并回调后端）与真机更新验收。

[2026-10-15] android-flutter - Flutter 重构完成宿主接线审计并补齐全部 A 级缺口（`android-flutter/` 仍未提交，逻辑层此前后已 1:1 移植并全绿）。对照 Kotlin `MainActivity.kt`/`MainShell.kt`/各 Screen 逐屏审计后修复：① `main.dart` 新增 `_UpdateHost` 宿主，挂载此前定义但从未实例化的 `UpdateDialog`（`showUpdateDialog` 无调用方），补 `PermissionRequired` 自动拉起 unknown-sources 设置页、返回后 `onInstallPermissionResult` 自动续装、`UpToDate`/`Failed(release==null)` 4 秒 toast；账户页新增版本行 + 手动检查更新（读 `UpdateDialogScope` 的 coordinator）。② `AppShell` 挂 `PopScope` 实现五个沉浸界面（PLAYER/FAVORITES/FUNDS/WITHDRAWAL/VIP_RECHARGE）的系统返回回上一级而非退出 App；消息条/认证面板/VIP 付费弹层提升到宿主 Stack 覆盖播放器，去掉 LOGIN 屏消息抑制，消息条以 `messageTrigger` 为 key 且消失时 `clearError()`。③ 播放器补右侧动作栏（点赞/评论/收藏）、评论弹层、奖励说明弹层与圆环点击、按 `visualState` 取色与 `estimatedRewardPoints != null` 显示门控、底部信息区（标题/简介/进度条/选集入口）；错误覆盖层从误接的 `rewardReportError` 改为 `video_player` 初始化/平台错误驱动，Retry 强制重建播放器（同 URL 也重建）并补 Next/Back；`rewardClaimed` 停止采样、后台暂停、采样统一 `playbackProgressSample`。④ 账户页补完整观看历史弹层；钱包弹层按 `walletSheetShouldDismiss` 在绑定/更换/解绑成功后都自动关闭（原解绑后不关）、补非空门控与地址预填、解绑确认改共享 `accountConfirmation*` 文案；改密补长度门控；补游客 Sign in / Create account 直达入口；提现记录行补 `network · address` 与 `withdrawalRecordDetail`；VIP 充值过期 PENDING 倒计时归零自动转创建分支；付费弹层 busy 补 `accountOperation`。⑤ 搜索页修复任意重建清空未提交输入的缺陷并补 Search 按钮/结果头/分组标题；首页空态接共享文案与去搜索动作；`AnnotatedRegion` 按主题设置系统栏图标。新增 `test/ui_wiring_test.dart` 5 条接线回归。验证：`flutter analyze` 零问题、`flutter test` 101 用例全绿、`flutter build apk --debug`（正式签名环境变量）通过。**已知剩余项**：重构尚未发布，`pubspec.yaml` 版本 `0.9.2+44` 待发布时用 `scripts/publish-android.py` 走既有链路；真机 c4e4da7e 上的目视验收未执行（装 debug 覆盖生产 0.9.1 后无法降级回装，需用户决定）。

[2026-10-15] android-flutter/真机验收 - 在真机 c4e4da7e（Android 14，1080×1920）完成首轮流式验收并修复三个只有真机能暴露的缺陷：① Discover 搜索页整页空白——分组标签 Column 被直接放进 `CustomScrollView.slivers`（`RenderFlex is not a subtype of RenderSliver`，重写前遗留的结构缺陷），包进 `SliverToBoxAdapter` 修复并新增挂载搜索页的 widget 冒烟测试；② **登录/注册成功后会话立即丢失**——`HttpReelShortApiClient` 的 `authenticated` 标志传给了 `_request()` 但从未传到 `_send()`，所有 GET 类鉴权接口（watch/history、points/account、orders、wallet、vip/orders、withdrawals 等）自重构以来从未带过 Bearer token，Fake 数据源 JVM 测试完全绕过 http 客户端故全绿；修复为 `_request` 返回 `(request, authenticated)` record、`_send` 解构取用。新增 `integration_test/acceptance_test.dart`（真机+真实后端长期验收资产，`flutter test integration_test -d <device> --dart-define=ACCEPTANCE_USERNAME=… --dart-define=ACCEPTANCE_PASSWORD=…`，凭据经 dart-define 注入不落库）锁定：登录会话稳定、openBook 拿到真实播放地址（38 集）、完整播放链路请求全带 Bearer。③ 防御性修复：冷启动 `restoreSession` 的安全存储首读在真机需数秒，用户在其完成前登录会被迟到的恢复覆盖新 token——repository 层 `currentToken != null` 时 restore 不覆盖、controller 层恢复完成时已登录则不覆盖 session/凭据。已验证：`flutter analyze` 零问题、`flutter test` 102 用例全绿、真机集成测试 2 用例全绿、Release APK（53.9MB，证书 SHA-256 `c1a149d5…45c2f9` 校验通过）真机安装运行，游客浏览/搜索/发现/认证面板触发（契约规则 39）真机截图复核通过。**已知剩余项**：`flutter test integration_test` 每次运行结束会卸载应用，跨进程会话持久化（登录→杀进程→冷启动恢复）需人工安装后复核；集成测试诊断用 `[api]` 请求日志保留为 `kDebugMode` 条件（release 自动剔除）。真机验收测试账号由 dart-define 注入，不入库。

[2026-10-15] android-flutter/release - 发布 ShortLink `0.9.2 (44)`（Flutter 版首个正式发布）：APK SHA-256 `16763812924e6226a13ab2899f56a40f134d210e25e577a2b0c213507e13d307`、56,664,444 字节，已上传 `releases/android/ShortLink-v0.9.2.apk` 与同名 `.sha256`，后端 `app_releases` 已 upsert，公开清单 `versionName/versionCode/apkSha256` 与预签名 URL 实际下载字节的哈希逐一比对一致。发布前提交两批：UI 评审打磨（标题 slug 回归、播放器手势交互、SafeArea、转场、触感等十项，见 `android-flutter/docs/parity-matrix.md` §8.1）与发布链路切换。`scripts/publish-android.py` 新增 `--source {flutter,kotlin}`（默认 flutter，Kotlin 版进入维护淘汰）：Flutter 走 `flutter build apk --release --build-name/--build-number`，flutter CLI 按 FLUTTER_BIN > PATH > 文档安装路径发现；包名、钉死证书 SHA-256 校验、摘要计算、COS 上传与后端发布逻辑不变，`scripts/tests` 11 用例通过。集成测试验收账号凭据已改为 --dart-define 注入，源码不含凭据。

[2026-10-15] android-flutter/update - 真机自动更新端到端验收并修复两处缺陷（提交 `77b3885`）：① **更新弹窗永不出现**——弹窗宿主 `_UpdateHost` 挂在 `MaterialApp.builder`（Navigator 之上），其 context 无法解析 Navigator，`showDialog` 抛 "No Navigator" 被静默吞掉：自动检查 HTTP 200、状态已 Available，但弹窗不显示、Me 页手动检查按钮也毫无反应。改为经 `MaterialApp.navigatorKey` 在 Navigator 之下弹窗。② **更新客户端全部请求缺超时**（Kotlin 版 OkHttp 有 connect 15s/read 60s/call 15min），黑洞连接会把状态机永久钉在 `Checking`、之后所有手动检查被 no-op 门禁拦下；新增 `updateManifestTimeout`(60s) 与 `updateDownloadCallTimeout`(15min) 并加 `kDebugMode` 的 `[update]` 请求日志。真机全链路复核（0.9.1/43 → 0.9.2/44）：冷启动弹窗 → Update now → 下载进度（13% 阶段可见进度条与 MB 计数）→ SHA-256 校验 → Ready to install → 系统安装器 → `dumpsys` 确认 versionCode=44；随后用修复版重发 0.9.2（同版本 44 覆盖 COS 与后端 upsert，新 SHA-256 `2a9ab018a9c285a379b07e3a0fc42fa770df5e56cca84aab2a92e753b60a255e`，公开清单已指向新包）并装回设备，冷启动无弹窗（同版本不提示，语义正确）。已知无关小瑕疵：弹窗标题行 `title · tagName` 会显示 "ShortLink v0.9.2 · v0.9.2"（发布脚本 title 与 tagName 同源，Kotlin 版同行为），留待下轮打磨。

[2026-10-14] android-app/release - 发布 ShortLink `0.9.1 (43)`（源 commit `36fc555`）：APK SHA-256 `a4608e50b3233ad453516d899fd0dad5de643c0b297333fcaf8d2b0c2c2c1724`、3,191,734 字节，已上传 `releases/android/ShortLink-v0.9.1.apk` 与同名 `.sha256`，后端 `app_releases` 已 upsert（`publishedAt` 2026-10-14T03:29:45Z）。发布机首次补齐 Android 工具链：Temurin JDK 17（`C:\Users\12801\jdk-17\jdk-17.0.20.1+1`）+ cmdline-tools/platform-tools/platforms;android-35/build-tools 34+35，`JAVA_HOME`/`ANDROID_HOME` 写入用户级环境变量，`android-app/local.properties` 指向本机 SDK（该文件与签名配置均被 gitignore）。已验证：`:app:testDebugUnitTest :app-core:test` 通过（app 119 用例 0 失败，含新增冷钱包副标题契约用例）、`scripts/tests` 发布脚本 11 用例通过、`apksigner` 对固定正式证书 SHA-256 校验通过、公开清单 `versionName/versionCode/apkSha256` 与预签名 URL 实际下载字节的哈希完全一致、发布 APK 的 `classes.dex` 含 `ERC20 wallets only` 且已无旧中文串。**已知剩余项**：本机没有模拟器或真机，`docs/deploy/release-checklist.md` 第 3 节的模拟器人工验收与 `verify-release-baseline.ps1 -InstallApk` 未执行，建议在设备上对 Me 页冷钱包入口补一次目视确认。

[2026-10-14] android-app/scripts - Me 页冷钱包入口在未绑定钱包时不再硬编码中文副标题「仅支持 ERC20 钱包」：新增本地化字段 `AppStrings.accountWalletNetworkHint`（`accountWalletTitle` 之后），`AccountScreen` 副标题改为 `?: copy.accountWalletNetworkHint`，英文取值为 `ERC20 wallets only`；`VisualTextContractTest` 新增 `accountWalletRowShowsLocalizedEnglishNetworkHint` 断言本地化文案并锁定该行不再出现中文硬编码。本次仅改 UI 文案，`app` 主源集字符串字面量已无中文（剩余中文仅存在于代码注释）。同时修复发布脚本 `scripts/publish-android.py` 的一处发布链路缺陷：`fetch_latest_release` 未显式带 `User-Agent`，Python 默认 UA 会被生产入口 Cloudflare 的 browser-integrity 校验拦成 403 `error code: 1010`，函数静默返回 `None`，导致 versionCode 预检被跳过、交互式版本号建议退化为 `1`（发布 POST 因原本就带 `ShortLinkPublisher/1.0` 未受影响）。现抽出模块常量 `PUBLISHER_USER_AGENT` 并同时用于读清单与发布 `POST`，`scripts/tests/test_publish_android.py` 9 → 11：新增 preflight UA 断言与「建议值必须为已发布 versionCode + 1」断言。

[2026-10-13] backend/operations - 修复 `watch-reward-task` 每次返回不同短剧第一集的问题：续播改为只认最近一条观看记录所属短剧（`findFirstByUserIdOrderByUpdatedAtDesc`），该集未领取奖励则原样返回该集、已领取则返回同一部短剧其后第一个未被领取的分集（逐集跳过已领奖集，容忍重看旧集造成的记录回退）；该短剧播完或用户没有观看记录时，才从 English 内容缓存随机取一部该用户**没有任何观看记录**的短剧的第 1 集（`content_book_cache` 用 `not exists` 子查询排除已看过的剧，以 `uk_watch_records_user_book_episode` 的 `(user_id, book_id)` 前缀做反连接探测，无新迁移/索引），第 1 集已领奖的短剧也按看过跳过。候选池保持 200 本有界窗口，并修正旧算法 `(total - window) / window` 导致末尾不足一屏的短剧永远取不到的取样偏差（生产片库 559 本时约 28% 的剧不可达）。权威时长解析改为区分「上游调用失败」与「上游确实没给时长」：失败时记 WARN 并放弃该短剧，不再在同一部剧内逐集重试上游；没有时长时继续试其后分集；最近那集本身取不到时长时会退化为换剧兜底（已在文档写明）。`docs/api/internal-operations.md` 规则段补齐失败语义、100% 未领奖语义与每日额度耗尽提示；清理因此变成死代码的 `content_book_cache` 全量/分页/计数查询。测试 `InternalOperationsControllerTests` 17 → 27：覆盖有未领取记录时重复 GET 稳定返回同一 (短剧, 分集)、看完一集续下一集、看完一部换未观看剧、只认最近记录、剧内跳过多个已领奖集、记录集号超出剧集列表、进度 100% 未领奖仍返回原集、看完全库返回 404、非 VIP 403、时长解析失败必须留 WARN，以及续播分支「调用内容源时不持有数据库事务」的断言。

[2026-10-12] infra/docs - 生产后端改为"展开目录 + 版本化 classes"薄更新形态：`ExecStart` 由 `-jar app.jar` 改为 `-cp /srv/reelshort/backend/classes:/srv/reelshort/backend/app/BOOT-INF/lib/* com.reelshort.backend.BackendApplication`，`classes` 是指向 `releases/classes-<版本>/classes` 的符号链接，日常改后端只需上传约 1.4 MB 的 `BOOT-INF/classes`（实测 fat jar 中 138.6 MB 是第三方依赖、仅 1.39 MB 是应用代码），不再重传 133 MB；回滚改为重指符号链接（秒级）或换回 `-jar app.jar`。新增 `infra/scripts/stage-backend-update.ps1`（构建 boot jar、只提取 classes、打印上传与切换指令；`bootJar` 的 `-plain.jar` 瘦包不可用于提取）并在 `docs/deploy/systemd.md` 补第 13 节"日常更新"表格（按改动类型给出上传量与是否重启）。

[2026-10-12] backend/content-provider/infra/docs - 生产部署到 `104.143.38.190` 并接入 `shortlink.hjj888.cc`（systemd 原生部署，见 `docs/deploy/systemd.md`）。修复两处只在真实部署中暴露的缺陷：① `spring.http.client.read-timeout` 由固定 `5s` 改为 `${REELSHORT_HTTP_READ_TIMEOUT:60s}`（连接超时 `${REELSHORT_HTTP_CONNECT_TIMEOUT:5s}`）——内容源冷缓存时单次上游抓取实测 4-15 秒，5 秒读超时会导致所有内容接口 503 且定时货架刷新永远失败；② 内容源上游站点地址改用独立的 `REELSHORT_UPSTREAM_SITE_URL`（`content-provider/app.py` 优先读取，仅为兼容旧部署回退 `REELSHORT_SITE_URL`，`infra/docker-compose.yml` 同步改键），避免把后端语义的"本平台公网域名"传给内容源导致其抓取自身、拿不到 Next.js `buildId` 而全站内容 502。部署同时确认：`SERVER_ADDRESS=127.0.0.1` 必须显式设置（Tomcat 默认监听 0.0.0.0，实测公网扫描器会直连 8080 请求 `/.env`、`/.git/HEAD`）；`REELSHORT_TRUSTED_PROXIES=127.0.0.1/32` 配合 `real_ip_header CF-Connecting-IP` 后限流按真实客户端计数（实测第 11 次请求返回 429）。

[2026-10-12] docs/plans - 新增 2026-10-12 全面代码审查修复的设计与实施计划（`2026-10-12-review-remediation-design.md` / `-implementation.md`），并逐份把 `docs/api/*.md`、`docs/deploy/*.md`、根 `README.md` 与 `AGENTS.md` 对齐到真实实现：重写认证文档为用户名 + 密码 + 图形验证码并标注短信/手机号链路已删除，删除 `zh-TW`/双语/按 locale 分桶表述改写为英文单语，补齐提现人工确认流水、作废 attempt 接口、`Idempotency-Key`、打款开关、银行卡 410、`403 invalid current password`/凭证锁定 429、新增限流规则与 CIDR 可信代理、部署面非 root/gunicorn/健康检查/备份落点、发布脚本新开关与 `ci.yml`。

[2026-10-12] backend/auth/security/system - 认证模型收敛为用户名 + 密码 + 4 位图形验证码并删除手机号/短信/AccountManager 回调与内部开户接口；验证码答案只以 SHA-256 哈希落库（`CaptchaChallenge.answer` 改为内存字段）、答错立即作废、新增 `CaptchaCleanupService` 定时清理过期挑战；新增 `CredentialAttemptGuard` 凭证失败锁定（默认 5 次 / 15 分钟，失败计数 `REQUIRES_NEW` 独立落库，锁定优先于 BCrypt 比对）；登录先比对密码再判状态以消除账号枚举；`CredentialLockedException` → 429、`IllegalStateException` → 409 `conflict`、`DataIntegrityViolationException` → 400；`X-Request-Id` 收紧为 `^[A-Za-z0-9_-]{8,64}$`。

[2026-10-12] backend/withdrawal/points/wallet - 人工确认提现在同一事务内补写负数 `WITHDRAWAL` 积分流水（余额快照 + `withdrawal:<申请 ID>` 幂等键），恢复"余额 = 流水累计"；新增 `POST /api/admin/withdrawals/{id}/payout-attempt/void` 与 `VOIDED` 终态，解除卡死 `ACTIVE` attempt 的死结（`BROADCASTED` 先只读链上核对）；提现提交支持可选 `Idempotency-Key` 并强制单笔 `PENDING`；自动打款执行能力由 `reelshort.withdrawal.payout.enabled` 门禁并默认关闭，三个执行 Bean 不装配、`approve`/`batch-*` 固定 410；银行卡提现下线（固定 410，删除卡号/CVV 采集与 `BankCardAttempt` 代码，表数据保留）；钱包密码错误改 403 并纳入凭证锁定；V31 新增 `credential_attempts`、`captcha_challenges.answer_hash`、`withdrawal_requests.idempotency_key`、两个热点索引与约束放宽，全部为新增/宽松化变更。

[2026-10-12] backend/content/watch/social/system/content-provider - 上游 HTTP 调用移出数据库事务，写库集中到 `ContentCacheWriter`，货架并发 miss 由 `ShelfSingleFlight` 去重；内容语言收敛为英文单语（`ContentLocale` 仅 `ENGLISH`，Flask `SUPPORTED_LOCALES={"en"}`），删除繁中片库关键词死配置与 SMS 相关环境变量；观看进度改为只允许前进；评论列表改分页返回 `{comments, hasMore, page, size}`（默认 20、上限 50），请求体补 `@Size`；内容源诊断告警改按事件水位线判定并新增每 5 分钟自动评估，避免告警长期 OPEN；内容源新增 `_as_int`/`_as_list` 容错与异常处理器（结构异常返回 502 而非 500）。

[2026-10-12] backend/order/release/system.config - VIP 订单允许管理员在链上核验通过后对 `EXPIRED` 订单做过期后补单（`recoverExpired` + `matchesLatePayment`，审计 `VIP_ORDER_RECOVERED_AFTER_EXPIRY`）；发布记录同名重发改为更新 `versionCode` 并拒绝回退；`vip.collection-address` 写入时校验 TRON 地址格式（允许清空），`SystemConfigUpdateRequest` 由 `@NotBlank` 改为 `@NotNull`。

[2026-10-12] android-app - 银行卡入口改为只读说明并删除全部卡号/CVV/持卡人采集与提交链路；VIP 收款二维码改为 `app-core` 本地 `QrMatrixEncoder` 生成，不再请求 `api.qrserver.com`；删除 GeoIP 地区封禁死代码（含向 `ipapi.co` 外发）、`GeoBlockedScreen` 与 GitHub Release 更新客户端；COS 下载域名收紧为精确桶域名并拒绝 versionCode 降级；APK SHA-256 计算与文件搬移移出主线程；受保护接口 401 清理本机会话，钱包/提现"当前密码错误"按 403 处理；401/403 语义与评论分页响应形态的客户端对齐仍未完成。

[2026-10-12] admin-web/infra/scripts - 后台删除不存在的"订单管理/支付事件"入口与 `phone*` 字段类型（账号唯一标识为 `username`），资金敏感系统配置保存前二次确认并展示新旧值，提现确认弹窗明确"不签名、不广播、不核验链上交易"；nginx 新增 CSP 与 `/assets/` 长缓存、`index.html` no-store；Compose 全服务 `restart: unless-stopped` 与日志轮转、nginx healthcheck 直连 `http://backend:8080/actuator/health`、`REELSHORT_TRUSTED_PROXIES`（支持 CIDR）改必填、`CONTENT_PROVIDER_INTERNAL_TOKEN` 贯通、backend 容器非 root（`reelshort`，日志 `/app/logs`）、content-provider 改 gunicorn + `nobody`；生产库转储迁入 `infra/backups/` 并补 `.gitignore`，`backup.ps1` 限制 `-OutputRoot` 必须落在仓库内；发布基线新增未提交已跟踪文件检查与 `-AllowDirtyTree`/`-SkipAndroidPackage`/`-SkipInfraChecks`/`-SkipScriptTests`/`-SkipAdminWebContracts`，新增 `.github/workflows/ci.yml`。

[2026-08-11] android-app/release/scripts/docs - 移除 ReelShort 的 Debug 证书签名路径；Debug 与 Release 变体统一使用恢复后的原正式 keystore，并在所有打包任务前钉死校验证书 SHA-256。Debug 仅保留可调试构建属性，不再代表 Debug 证书。

[2026-08-10] android-app/release/scripts/docs - 从 Windows 卷影副本恢复原正式 keystore 和本地签名配置，桌面增加受限 ACL 备份；Debug 构建保留固定 Debug 证书，线上 Release 恢复 `ANDROID_SIGNING_*` 正式签名并钉死原证书 SHA-256。Debug 签名 `0.8.7 (41)` 用户切回正式签名版本时需卸载重装。

[2026-08-10] android-app/release/scripts/docs - Android 全部切换为同一固定 Debug keystore 签名；优化 Release 变体保留压缩和非 debuggable 属性，发布脚本钉死 Debug 证书 SHA-256，删除正式 JKS、DPAPI 备份和 `ANDROID_SIGNING_*` 配置；已装正式签名 `0.8.6 (40)` 的设备需卸载一次，之后可连续覆盖升级。

[2026-08-10] backend/content/points/android-app/player - 播放奖励预计值改为结合登录用户十分位尾数与当日剩余额度计算实际整数入账；奖励结算后播放器圆环改用服务端实际到账积分，避免圆环数字与 `+N pt` 浮层不一致。

[2026-08-10] android-app/account - 资金中心改为底部一级 Tab；移除 Me 页“提现与 VIP”入口和重复订单入口，VIP 充值记录集中展示在资金中心。

[2026-08-10] backend/points/android-app - 积分账户摘要新增上海时区当天正向入账积分 `todayCreditedPoints`；Android “我的”页新增资金中心入口，并提供独立提现与 VIP 充值页面，页面共享现有提现和 VIP 订单状态。

[2026-08-07] backend/content/android-app/player - 播放接口新增按权威时长和当前积分配置实时计算的 `estimatedRewardPoints`；播放器奖励胶囊改为无外框的积分图标与确定型进度圆环，圆心显示本集预计基础积分并保留 TalkBack 与实际到账反馈。

[2026-08-07] backend/migration - 从原始功能分支恢复生产已执行的 `V30__track_first_app_login.sql`，以 checksum `-423089945` 补齐 Flyway 历史源码；仅增加可空列，不合并旧分支的登录限制逻辑。

[2026-08-07] backend/release/scripts - Android 发布说明允许保持为空，发布脚本不再在省略 `--notes` 时自动写入默认更新文案；正式 APK 继续由固定 SHA-256 的 release 证书校验后发布。

[2026-08-06] android-app - 第 8 集及以后锁集点击改为在详情页或全屏播放器原地打开共享 TRC20 VIP 付费弹层；游客登录后自动进入付费层，订单确认后自动续播目标分集，Me 页复用同一全局弹层。

[2026-08-04] backend/operations/content/system - 观看奖励任务查询移除长事务边界，内容源现拉不再持有数据库事务；默认数据库连接池上限提高到 30。

[2026-08-04] backend/social - 伪评论集合保持按短剧稳定生成，但每次读取随机打乱展示顺序，并移除用户名数字后缀。

[2026-08-04] backend/social - 社交展示 fallback 改为按 `bookId` 稳定分层随机热度，热门短剧可显示 1k+ 点赞和 100+ 评论，真实评论仍在前且伪评论不落库。

[2026-08-04] backend/social - 社交展示 fallback 改为与真实点赞、收藏、评论合并显示，真实评论优先、伪评论补充且不落库。

[2026-08-03] admin-web/android-app/withdrawal - 后台提现申请操作列新增复制钱包地址按钮；App 提现数量输入框空闲时提示到账约 1 天。

[2026-08-03] android-app/account - Me 页面新增独立提现记录入口，提现快捷卡继续打开提交表单，记录入口单独展示历史提现列表。

[2026-07-24] android-app/account - Me 页面新增所有用户可见的联系客服入口，弹窗展示客服邮箱 `shortlink@gmail.com` 供用户咨询。
[2026-07-23] backend/operations - `watch-reward-task` 候选短剧改为随机取书，但单书内仍按集数顺序取下一集，避免运营任务长期固定按同一顺序轮转。

[2026-07-23] android-app/release - 取消 App 下载完成后的包名、versionCode 和签名自校验阻断，仅保留 SHA-256 完整性校验并交由 Android 系统安装器执行最终安装校验，避免 `PackageManager` 预检误判导致线上用户无法更新。

[2026-07-23] backend/admin-web/withdrawal - 打款统计新增自定义日期范围查询：后台可按上海时区整天选择开始/结束日期，后端 `/api/admin/withdrawals/stats` 支持 `range=CUSTOM&fromDate=yyyy-MM-dd&toDate=yyyy-MM-dd` 并保持原预设范围不变。

[2026-07-22] backend/admin-web/infra - 旧版 Android 更新链路彻底下线：`/api/app/update/latest` 不再被 App 鉴权拦截而是返回 404，`/downloads/android` 及其子路径在 Nginx 层显式返回 404，避免落入后台 SPA。

[2026-07-22] backend/release/infra/docs - 删除旧版 Android 更新兼容链路：移除 `/api/app/update/latest`、`/downloads/android/` 后端重定向、Nginx 转发和相关文档，只保留 `/api/app/release/latest` 返回 COS 预签名下载 URL。

[2026-07-22] backend/withdrawal/backend/release/backend/social/scripts - 恢复提现最低 `10 USDT` 配置和动态最低积分计算；旧版 Android 更新清单回退为 `shortlink.hjj888.cc/downloads/android/...` 稳定 URL，并由后端 302 到 COS；发布脚本新增正式证书 SHA-256 和 versionCode 钉死校验；社交接口在无真实数据时生成稳定计数与伪评论 fallback。

[2026-07-22] backend/admin-web/order/withdrawal - 全面移除管理员 TOTP/2FA：管理员仅以账号密码建立后台会话，删除 2FA 页面、接口、限流规则、VIP 确认验证码与禁用自动打款 DTO 中的验证码字段；V29 物理删除 `admin_users` 的 TOTP 密钥和启用列，VIP 人工确认继续要求 `ORDER_WRITE` 与 txHash 链上核验。

[2026-07-22] android-app/admin-web/docs - 移除已取消的积分交易后台残留请求、类型和详情标签，避免不存在的 `/point-transfers` 接口导致用户详情失败；Android 更新弹窗不再展示发布说明。

[2026-07-22] backend/admin-web/android-app/auth - 注册和改密密码统一为至少 8 位，App 显示长度提示；有 `USER_WRITE` 权限的管理员可直接设置未来 VIP 到期时间或取消当前权益，写入审计但不创建或修改 VIP 订单。

[2026-07-22] backend/admin-web/android-app/withdrawal - 提现换算改为后台直接配置每 50 积分对应 USDT，默认 0.14；保留积分手续费，实际到账向下截断到 2 位且最低 0.01 USDT，最低积分动态计算；V28 新增比例快照但不重算历史申请，Android 0.7.4 (27) 仅显示最低 USDT、最低积分和本次预计到账。

[2026-07-21] backend/admin-web/withdrawal - ERC20 外部钱包单笔确认取消二次 TOTP：接口改为无请求体，后台保留已登录管理员的 `WITHDRAWAL_WRITE` 权限校验、外部打款提示、幂等扣除冻结积分与审计；管理员登录和 VIP 订单确认的 TOTP 不变。

[2026-07-21] backend/admin-web/android-app/withdrawal - 提现收敛为 ERC20：停用自动签名、广播、余额预检和链上确认入口，管理员完成外部钱包转账后仅以 2FA 单笔确认并扣除冻结积分；后台新增今天、昨天、本周、本月、上月的已确认 ERC20 打款笔数与金额统计；V27 自动拒绝历史待处理非 ERC20 提现并释放冻结积分，历史记录保留只读。

[2026-07-21] backend/withdrawal/infra - 公共 TronGrid 无 API Key 场景新增约 4 req/s 的统一 RPC 启动节流、可安全重放请求的 429 `Retry-After`/1-2-4 秒退避重试和 5 分钟链费率缓存；重试耗尽返回中文“打款未执行”，签名交易广播保持单次发送不自动重试，Compose 与样例配置新增对应可调参数及可选 API Key。

[2026-07-21] backend/withdrawal - TRC20 最终打款余额预检改为逐笔只读模拟精确转账，按实时 Energy/带宽价格扣除热钱包可用资源并增加 20% 安全余量；`feeLimit` 继续作为单笔交易最大链上费用保护，不再按上限乘笔数误判所需 TRX，预览继续明确展示无私钥场景的预计上限。

[2026-07-21] backend/withdrawal/admin-web - 提现预览新增按网络聚合的 TRX/ETH/BNB 预计手续费，TRON 标记 feeLimit 预计上限，EVM 按当前 gasPrice 与 gasLimit 估算；V26 为 payout attempt 增加实际手续费字段，链上确认时从 TRON transaction info 或 EVM receipt 幂等持久化，后台仅对 CONFIRMED attempt 展示实际原生币手续费。

[2026-07-20] backend/withdrawal/admin-web - 余额预检失败改为中文返回所需、当前和差额，后台提现安全确认弹窗持久显示“打款未执行”错误，同时继续在请求结束后清空私钥与 2FA。

[2026-07-20] backend/withdrawal/admin-web - 提现单笔/批量打款新增按网络聚合的 USDT 与 TRX/ETH/BNB 余额预检，余额不足时在创建 attempt 前整体拒绝并用执行锁降低并发超支；后台禁止选择已有 PREPARED、BROADCASTED、MANUAL_REVIEW、CONFIRMED attempt 的申请，仅保留首次执行、SIGNING 恢复和 FAILED_RETRYABLE 重试。

[2026-07-20] backend/withdrawal - 修复 TRC20 transfer 收款地址 ABI 编码错误：Base58Check 解码后只使用去掉 `0x41` 网络前缀的 20 字节 payload，不再把 4 字节 checksum 误编码为收款地址；新增真实 trigger HTTP 请求参数回归测试。

[2026-07-20] backend/withdrawal - 修复 TRON 未签名交易意图校验稳定失败时无法定位的问题：保留 Trident protobuf 严格校验，首次字段不匹配时只向节点重建一次，第二次返回字段级安全原因码；HTTP、节点业务错误、缺失原始交易和 `txID` 不匹配不重试，日志不记录私钥、签名或完整 `raw_data_hex`。

[2026-07-20] backend/withdrawal/infra - 将 TRC20、ERC20、BEP20 预期热钱包公开地址改为可选白名单：未配置时使用管理员临时提交私钥的派生地址继续打款，配置后仍严格校验；签名阶段始终复核派生地址与持久化 intent，Compose 补齐 BSC 地址透传。

[2026-07-20] backend/admin-web/android-app - 新增 BEP20（币安链/BSC）提现打款支持：后端新增 `BscProperties`（`reelshort.bsc.*`，USDT 18 decimals 参数化）和 `BscClient`（web3j，独立于 EthereumClient 避免金额换算与 network 标识错配），`WithdrawalPayoutCoordinator` 三处链分支扩展为显式 TRC20/ERC20/BEP20（修复原 `replay` 的 `else` 兜底会把 BEP20 错用 ERC20 签名的隐患），`WithdrawalPayoutTransactionService` 新增 `reserveBep20`（nonce 按 `(network, wallet, chainId)` 隔离），`WithdrawalPayoutConfirmationService` 抽取 `queryChainStatus`/`requiredConfirmationsFor` 分发，`WithdrawalService.approve/batchApprove/batchPreview` 与 `AdminWithdrawalController` 请求 DTO 增加 `bepPrivateKey`/`bepHotWalletAddress`，`WalletService` 放开 BEP20 钱包绑定复用 EVM 地址校验；admin-web 提现管理表单新增 BEP20 私钥输入与热钱包地址预览；Android 钱包绑定 FilterChip 新增 BEP20 选项；后端测试新增 `BscClientTests`、BEP20 fail-closed 与端到端集成回归。本次仅做提现打款，不涉及 VIP 收款（order 模块 ERC20 收款本就未接入），无需 Flyway 迁移（network 列 varchar(16) 足够，hot_wallet_nonces 唯一键含 network 天然隔离 BSC nonce）。

[2026-07-19] backend/operations - 修复运营观看奖励任务接口 `watch reward task not found` 候选池过窄问题：`taskFromContentCache` 改为通过 `ContentCacheService.getEpisodes` 获取剧集列表（episode_cache 缺失时向上游 provider 现拉并回填），候选池从「仅有 episode_cache 的少数剧」扩展到全部 ENGLISH 剧；新增 `findClaimedKeysByUserId` 投影一次性预取已领奖集合消除 N+1，上游 5xx/404 时跳过该本书不阻断整体遍历；清理 service 内成为死代码的 episode 反序列化 helper、objectMapper 和 episodeCacheRepository 依赖；测试新增 `@MockitoBean ContentProvider` 桩与 `watchRewardTaskFallsBackToLiveProviderWhenEpisodeCacheMissing` 回归保护。

[2026-07-18] backend/admin-web/points/wallet/content-provider/android-app - 禁用旧充值客户端下单并保留历史订单只读/内部结算；钱包绑定、更换、解绑增加当前密码和 ERC20 零地址校验；后台积分调整增加作用域幂等键，公平模式统一余额、流水、领取记录和响应的整数进位；content-provider HTTPS 固定 IP 连接保留原始域名 SNI/证书校验/Host；Android 同步钱包密码和 VIP 收款快照 DTO，清理过期积分转账测试契约。

[2026-07-18] backend/admin/order/withdrawal/system - 加固 VIP 人工收款确认与拒绝事务审计、2FA enable 状态机和敏感接口限流；统一交易哈希小写并新增 V24 规范化约束；限制 Tron 配置、响应体和分页指纹边界。

[2026-07-17] backend/admin/order - VIP 收款扫描改为按订单地址与合约快照分组，新增 V23 持久化 fingerprint cursor 以跨轮续扫分页硬上限；精确候选保留 CONFIRMED/DEFER/FAILED 验证态，receipt 未成熟或确认数不足时停留当前页，明确失败时写系统审计并继续扫描；手工确认按 txHash、地址、合约和应付金额精确选择 Transfer event；补齐并发尾差、权益续期和真实后台 RBAC 测试；已启用 TOTP 的管理员登录必须同时提交 6 位动态码。
[2026-07-17] backend/admin-web/order - 加固 VIP TRC20 收款：映射 V20 订单快照，增加数据库串行分配与单用户活动订单幂等，按 fingerprint 分页并以 receipt 和当前块核验地址、合约、精确金额、付款时间、成功状态和真实确认数，增加确认宽限与系统审计，人工确认要求 ORDER_WRITE 和 TOTP，后台展示真实应付金额、确认信息与过期状态。

[2026-07-17] backend/admin-web/infra/withdrawal - 后台提现管理对接持久化 payout attempt：预览接口移除私钥和派生余额查询并要求提现读写双权限，单笔/批量最多处理 10 笔并按网络逐次提交严格 hex 私钥与 6 位 TOTP，仅 BROADCASTED、CONFIRMED 计为已提交，PREPARED 单独显示待广播；审计失败只记录 WARN 且不改变打款结果，前端使用 120 秒超时并将 502/504/客户端超时按后台可能仍在处理提示，Compose 与宿主机 Nginx 仅为打款执行路由配置 150 秒代理超时。

[2026-07-17] backend/withdrawal/infra - 加固自动打款签名与状态一致性：TRON 使用 Trident protobuf 校验节点原始交易，过期未找到转人工核对，ERC20 nonce 与签名 intent 原子保存，统一 withdrawal/attempt 锁顺序，并新增受控热钱包地址 fail-closed 配置。

[2026-07-14] android-app/backend/infra/release - Android 自动更新切换到腾讯云 COS 对象存储：新增 `scripts/publish-android.py` 本地交互式打包上传脚本，后端新增 `release` 模块（Flyway `app_releases` 表 + COS Java SDK 按需生成短时预签名 URL + `/api/app/release/latest` 游客可访问 + `/api/internal/release/publish` 超级 Token 保护），App 放宽 `ShortLinkUpdateClient` URL 校验以接受 COS 预签名链接，删除 GitHub Actions SSH 发布工作流，Nginx 移除 `/downloads/android` 静态下载和限速块，Compose 移除 `releases/android` 只读挂载。

[2026-07-14] android-app/release - 发布 `0.4.1 (5)` 使用新的自托管 APK URL，避开切换限速策略前由 CDN 缓存的旧 `0.4.0` 错误响应。
[2026-07-14] android-app/infra/release - App 更新源从 GitHub Releases 切换为 `shortlink.hjj888.cc` 自托管清单与 APK，Nginx 按 IP 单连接并在前 2 MiB 后限制 1 MiB/s，签名工作流通过低权限 SSH 用户原子发布 `0.4.0 (4)`。
[2026-07-14] android-app/release - 正式发布流水线改为快速模式，移除 Release 阶段的单元测试、Lint 和 Compose 模拟器测试，仅保留版本、正式签名、APK 元数据与摘要校验。
[2026-07-14] android-app/release - 将 ShortLink 版本提升至 `0.3.0 (3)`，用于正式签名自动更新端到端验收。

[2026-07-13] backend/android-app - 修复完成奖励审查问题：服务端按权威时长判定完成，迁移当天回填旧奖励额度，规则配置切换保持次日生效，观看历史与快照返回真实领取状态；Android 完成事件即时上报并排队，到账和提现成功不再被后续刷新失败覆盖。
[2026-07-13] backend/android-app/admin-web - 观看奖励改为按服务端视频时长在完成时每集一次性发放，新增每日账号随机下浮额度，并将提现改为人民币积分比例与 CNY/USD 汇率换算。

[2026-07-13] backend/points - 新增每账号每日自动获取积分上限配置 `points.daily-earned.maximum`，观看奖励和运营模拟观看按服务器自然日共享额度。
[2026-07-13] infra/nginx - 生产 Nginx 取消 `/api/internal` 404 拦截，公网 internal 接口改为透传 backend 并依赖 `X-Internal-Super-Token` 鉴权。
[2026-07-13] android-app/release - 新增基于 GitHub Releases 的稳定版检测、App 内 APK 下载进度、摘要与签名校验、系统安装引导和自动签名发布工作流；仓库采用 Apache-2.0 公开发布。
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
